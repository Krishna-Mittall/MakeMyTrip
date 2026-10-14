package com.makemytrip.makemytrip.services;
import com.makemytrip.makemytrip.exceptions.ApiException;
import com.makemytrip.makemytrip.models.PriceFreeze;
import com.makemytrip.makemytrip.models.RoomType;
import com.makemytrip.makemytrip.models.Users;
import com.makemytrip.makemytrip.models.Users.Booking;
import com.makemytrip.makemytrip.models.Flight;
import com.makemytrip.makemytrip.models.Hotel;
import com.makemytrip.makemytrip.repositories.UserRepository;
import com.makemytrip.makemytrip.repositories.FlightRepository;
import com.makemytrip.makemytrip.repositories.HotelRepository;
import com.makemytrip.makemytrip.util.TimeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Service
public class BookingService {
    private static final String REFERENCE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FlightRepository flightRepository;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private SeatService seatService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private PricingService pricingService;

    @Autowired
    private FlightStatusService flightStatusService;

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private EventHub eventHub;

    public Booking bookFlight(String userId,String flightId,int seats,double price){
        return bookFlight(userId,flightId,seats,price,null,null);
    }

    public synchronized Booking bookFlight(String userId,String flightId,int seats,double price,String seatNumbers,String freezeId){
        Optional<Users> usersOptional =userRepository.findById(userId);
        Optional<Flight> flightOptional =flightRepository.findById(flightId);
        if(usersOptional.isPresent() && flightOptional.isPresent()){
            Users user=usersOptional.get();
            Flight flight=flightOptional.get();
            if(seats < 1){
                throw new ApiException(HttpStatus.BAD_REQUEST, "Select at least one seat");
            }
            if(flight.getAvailableSeats() >= seats){
                List<String> seatList = splitList(seatNumbers);
                if(!seatList.isEmpty() && seatList.size() != seats){
                    throw new ApiException(HttpStatus.BAD_REQUEST, "The number of selected seats does not match the number of tickets");
                }
                String reference = newReference();
                if(seatList.isEmpty()){
                    seatList = seatService.pickSeats(flight, seats, userId);
                }

                // the price is worked out by the dynamic pricing engine (or taken from the user's price freeze)
                PriceFreeze freeze = (freezeId == null || freezeId.isBlank()) ? null
                        : pricingService.validateFreeze(freezeId, userId, PricingService.FLIGHT, flightId);
                double unit = PricingService.effectivePrice(freeze, pricingService.quoteFlight(flight).getCurrentPrice());
                double seatCharges = 0;
                for(String seat : seatList){
                    seatCharges += seatService.chargeOfSeat(flightId, seat);
                }
                double minimum = unit * seats + seatCharges;
                if(price + 1 < minimum){
                    throw new ApiException(HttpStatus.CONFLICT, "The price has changed to Rs " + Math.round(minimum)
                            + " (before taxes). Please review the fare and try again.");
                }
                seatService.confirm(flight, seatList, userId, reference);

                flight.setAvailableSeats(flight.getAvailableSeats()- seats);
                flightRepository.save(flight);

                Booking booking=new Booking();
                booking.setType("Flight");
                booking.setBookingId(flightId);
                booking.setDate(LocalDate.now().toString());
                booking.setQuantity(seats);
                booking.setTotalPrice(price);
                booking.setReference(reference);
                booking.setStatus("CONFIRMED");
                booking.setCreatedAt(Instant.now().toString());
                Instant departure = TimeUtil.parse(flight.getDepartureTime());
                booking.setTravelDate(departure == null ? null : departure.toString());
                booking.setTitle(flight.getFlightName());
                booking.setSubtitle(flight.getFrom() + " → " + flight.getTo());
                booking.setUnitPrice(unit);
                booking.setSeatCharges(seatCharges);
                booking.setSeats(seatList);
                user.getBookings().add(booking);
                userRepository.save(user);

                pricingService.markFreezeUsed(freeze, reference);
                pricingService.publishPrice(PricingService.FLIGHT, flightId);
                try {
                    flightStatusService.track(userId, flightId, "BOOKING", reference);   // follow the booked flight automatically
                    recommendationService.log(userId, "FLIGHT", flightId, "BOOK", null);
                } catch (RuntimeException ignored) {
                }
                return booking;
            }else {
                throw new RuntimeException("Not enough seats available");
            }
        }
        throw new RuntimeException("User or flight not found");
    }
    public Booking bookhotel(String userId,String hotelId,int rooms,double price){
        return bookhotel(userId,hotelId,rooms,price,null,null,1,null);
    }

    public synchronized Booking bookhotel(String userId,String hotelId,int rooms,double price,String roomType,String checkIn,int nights,String freezeId){
        Optional<Users> usersOptional =userRepository.findById(userId);
        Optional<Hotel> hotelOptional = hotelRepository.findById(hotelId);
        if(usersOptional.isPresent() && hotelOptional.isPresent()){
            Users user=usersOptional.get();
            Hotel hotel=hotelOptional.get();
            if(rooms < 1){
                throw new ApiException(HttpStatus.BAD_REQUEST, "Select at least one room");
            }
            if(hotel.getAvailableRooms() >= rooms){
                LocalDate today = TimeUtil.now().toLocalDate();
                LocalDate checkInDate = today;
                if(checkIn != null && !checkIn.isBlank()){
                    try {
                        checkInDate = LocalDate.parse(checkIn);
                    } catch (DateTimeParseException e) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "Check-in date must look like 2026-12-31");
                    }
                }
                if(checkInDate.isBefore(today)){
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Check-in date cannot be in the past");
                }
                int stay = Math.max(1, Math.min(nights, 30));
                String typeName = (roomType == null || roomType.isBlank()) ? "Standard" : roomType;
                RoomType type = roomService.find(hotel, typeName);

                PriceFreeze freeze = (freezeId == null || freezeId.isBlank()) ? null
                        : pricingService.validateFreeze(freezeId, userId, PricingService.HOTEL, hotelId);
                double current = pricingService.quoteHotel(hotel, checkInDate).getCurrentPrice();
                double nightly = Math.round(PricingService.effectivePrice(freeze, current) * type.getPriceMultiplier());
                double minimum = nightly * rooms * stay;
                if(price + 1 < minimum){
                    throw new ApiException(HttpStatus.CONFLICT, "The price has changed to Rs " + Math.round(minimum)
                            + " (before taxes). Please review the fare and try again.");
                }
                roomService.takeRooms(hotel, typeName, rooms);

                hotel.setAvailableRooms(hotel.getAvailableRooms()- rooms);
                hotelRepository.save(hotel);

                Booking booking=new Booking();
                booking.setType("Hotel");
                booking.setBookingId(hotelId);
                booking.setDate(LocalDate.now().toString());
                booking.setQuantity(rooms);
                booking.setTotalPrice(price);
                booking.setReference(newReference());
                booking.setStatus("CONFIRMED");
                booking.setCreatedAt(Instant.now().toString());
                booking.setTravelDate(checkInDate.atTime(14, 0).atZone(TimeUtil.IST).toInstant().toString());
                booking.setTitle(hotel.gethotelName());
                booking.setSubtitle(hotel.getLocation());
                booking.setNights(stay);
                booking.setUnitPrice(nightly);
                booking.setRoomType(type.getName());
                user.getBookings().add(booking);
                userRepository.save(user);

                pricingService.markFreezeUsed(freeze, booking.getReference());
                eventHub.publish("ROOM_UPDATE", Map.of("hotelId", hotelId));
                pricingService.publishPrice(PricingService.HOTEL, hotelId);
                try {
                    recommendationService.log(userId, "HOTEL", hotelId, "BOOK", null);
                } catch (RuntimeException ignored) {
                }
                return booking;
            }else {
                throw new RuntimeException("Not enough rooms available");
            }
        }
        throw new RuntimeException("User or flight not found");
    }

    // ---------------------------------------------------------------- helpers

    public String newReference(){
        Random random = new Random();
        while (true) {
            StringBuilder sb = new StringBuilder("MMT");
            for (int i = 0; i < 6; i++) {
                sb.append(REFERENCE_CHARS.charAt(random.nextInt(REFERENCE_CHARS.length())));
            }
            String reference = sb.toString();
            if(userRepository.findByBookingsReference(reference) == null){
                return reference;
            }
        }
    }

    /** Bookings made before the cancellation feature existed get a reference so that they can be cancelled too. */
    public void ensureReferences(Users user){
        if(user == null || user.getBookings() == null){
            return;
        }
        boolean changed = false;
        for(Booking b : user.getBookings()){
            if(b.getReference() == null){
                b.setReference(newReference());
                changed = true;
            }
            if(b.getCreatedAt() == null){
                Instant created = TimeUtil.parse(b.getDate());
                b.setCreatedAt(created == null ? Instant.now().toString() : created.toString());
                changed = true;
            }
        }
        if(changed){
            userRepository.save(user);
        }
    }

    private List<String> splitList(String value){
        List<String> list = new ArrayList<>();
        if(value != null){
            for(String s : value.split(",")){
                if(!s.isBlank()){
                    list.add(s.trim().toUpperCase());
                }
            }
        }
        return list;
    }

}
