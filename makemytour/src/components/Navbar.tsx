import React from "react";
import SignupDialog from "./SignupDialog";
import { Home, LayoutDashboard, LogOut, Plane, PlaneTakeoff, User } from "lucide-react";
import NotificationBell from "./NotificationBell";
import { useDispatch, useSelector } from "react-redux";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Button } from "./ui/button";
import { Avatar, AvatarFallback } from "./ui/avatar";
import { clearUser } from "@/store";
import { useRouter } from "next/navigation";
const Navbar = () => {
  const dispatch = useDispatch();
  const user = useSelector((state: any) => state.user.user);
  const router = useRouter();
  const logout = () => {
    dispatch(clearUser());
  };
  return (
    <header className=" backdrop-blur-md py-4 sticky top-0 z-50">
      <div className="container mx-auto px-4 flex items-center justify-between">
        <div
          className="flex items-center space-x-2 text-white cursor-pointer"
          role="link"
          aria-label="MakeMyTour home"
          onClick={() => router.push("/")}
        >
          <Plane className="w-6 h-6 sm:w-8 sm:h-8 text-red-500" />
          <span className="text-xl sm:text-2xl font-bold text-black">MakeMyTour</span>
        </div>
        <div className="flex items-center space-x-1 sm:space-x-4">
          <Button variant="ghost" className="px-2 sm:px-4" onClick={() => router.push("/")}>
            <Home className="w-4 h-4 sm:mr-2" />
            <span className="hidden sm:inline">Home</span>
          </Button>
          <Button variant="ghost" className="px-2 sm:px-4" onClick={() => router.push("/flight-status")}>
            <PlaneTakeoff className="w-4 h-4 sm:mr-2" />
            <span className="hidden sm:inline">Flight Status</span>
          </Button>
          {user ? (
            <>
              <NotificationBell />
              {(user.role === "ADMIN" || user.role === "MODERATOR") && (
                <Button variant="default" className="px-3 sm:px-4" onClick={() => router.push("/admin")}>
                  {user.role === "ADMIN" ? "ADMIN" : "MODERATE"}
                </Button>
              )}

              <Button variant="ghost" className="px-2 sm:px-4" onClick={() => router.push("/profile")}>
                <LayoutDashboard className="w-4 h-4 sm:mr-2" />
                <span className="hidden sm:inline">My Dashboard</span>
              </Button>

              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button
                    variant="ghost"
                    className="relative h-8 w-8 rounded-full"
                  >
                    <Avatar className="h-8 w-8">
                      <AvatarFallback>
                        {user?.firstName?.charAt(0)}
                      </AvatarFallback>
                    </Avatar>
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent className="w-56" align="end" forceMount>
                  <DropdownMenuLabel className="font-normal">
                    <div className="flex flex-col space-y-1">
                      <p className="text-sm font-medium leading-none">
                        {user?.firstName}
                      </p>
                      <p className="text-xs leading-none text-muted-foreground">
                        {user?.email}
                      </p>
                    </div>
                  </DropdownMenuLabel>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem onClick={() => router.push("/profile")}>
                    <User className="mr-2 h-4 w-4" />
                    <span>My Dashboard</span>
                  </DropdownMenuItem>
                  <DropdownMenuItem onClick={() => logout()}>
                    <LogOut className="mr-2 h-4 w-4" />
                    <span>Log out</span>
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </>
          ) : (
            <>
              <SignupDialog
                defaultMode="login"
                trigger={
                  <Button variant="ghost" className="px-2 sm:px-4">
                    Login
                  </Button>
                }
              />
              <SignupDialog
                trigger={
                  <Button
                    variant="outline"
                    className="bg-blue-600  text-white hover:bg-blue-700 px-3 sm:px-4"
                  >
                    Sign Up
                  </Button>
                }
              />
            </>
          )}
        </div>
      </div>
    </header>
  );
};

export default Navbar;
