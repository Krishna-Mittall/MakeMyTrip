package com.makemytrip.makemytrip.controllers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.makemytrip.makemytrip.models.Users;
import com.makemytrip.makemytrip.services.BookingService;

@RestController
@RequestMapping("/booking")
public class BookingController {
    @Autowired
    private BookingService bookingService;

    @PostMapping("/flight")
    public Users.Booking bookFlight(@RequestParam String userId,@RequestParam String flightId,@RequestParam int seats,@RequestParam double price,
                                    @RequestParam(required = false) String seatNumbers,@RequestParam(required = false) String freezeId,
                                    @RequestParam(required = false) String promoCode){
        return bookingService.bookFlight(userId,flightId,seats,price,seatNumbers,freezeId,promoCode);
    }
    @PostMapping("/hotel")
    public Users.Booking bookhotel (@RequestParam String userId,@RequestParam String hotelId,@RequestParam int rooms,@RequestParam double price,
                                    @RequestParam(required = false) String roomType,@RequestParam(required = false) String checkIn,
                                    @RequestParam(defaultValue = "1") int nights,@RequestParam(required = false) String freezeId){
        return bookingService.bookhotel(userId,hotelId,rooms,price,roomType,checkIn,nights,freezeId);
    }
}
