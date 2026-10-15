import React, { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "./ui/dialog";
import { Button } from "./ui/button";
import { DialogDescription } from "@radix-ui/react-dialog";
import { Label } from "./ui/label";
import { Input } from "./ui/input";
import { signup, login } from "../api";
import { setUser } from "@/store";
import { useDispatch } from "react-redux";
import { useRouter } from "next/router";
const SignupDialog = ({trigger, defaultMode = "signup", redirectHome = true}:any) => {
  const router = useRouter();
  const [isSignup, setIsSignup] = useState(defaultMode !== "login");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [open, setopem] = useState(false);
  const dispatch = useDispatch();
  const handleAuth = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setMessage("");
    if (isSignup) {
      try {
        await signup(
          firstName,
          lastName,
          email,
          phoneNumber,
          password
        );
        // account is created - now ask the user to sign in with it
        setIsSignup(false);
        setFirstName("");
        setLastName("");
        setPhoneNumber("");
        setPassword("");
        setMessage("Account created successfully! Please log in to continue.");
      } catch (error: any) {
        console.log(error);
        setError(
          error?.response?.data?.message ||
            "Could not create the account. This email may already be registered."
        );
      }
    } else {
      try {
        const data = await login(email, password);
        if (!data) {
          setError("Invalid email or password.");
          return;
        }
        dispatch(setUser(data));
        setopem(false);
        clearform();
        // after a login (also when somebody switches to another account) start again from the home page
        if (redirectHome) router.push("/");
      } catch (error) {
        console.log(error);
        setError("Could not log in right now. Please try again in a moment.");
      }
    }
  };
  const switchMode = (signupMode: boolean) => {
    setIsSignup(signupMode);
    setError("");
    setMessage("");
  };
  const clearform = () => {
    setFirstName("");
    setLastName("");
    setEmail("");
    setPassword("");
    setPhoneNumber("");
  };
  const handleOpenChange = (value: boolean) => {
    setopem(value);
    if (value) {
      setIsSignup(defaultMode !== "login");
      setError("");
      setMessage("");
    }
  };
  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        {trigger}
      </DialogTrigger>
      <DialogContent className="sm:max-w-[425px] bg-white">
        <DialogHeader>
          <DialogTitle className="text-2xl font-bold">
            {isSignup ? "Create Account" : "Welcome Back"}
          </DialogTitle>
          <DialogDescription>
            {isSignup
              ? "Join us to start booking your travels."
              : "Enter your credentials to access your account."}
          </DialogDescription>
        </DialogHeader>
        {message && (
          <p className="text-sm rounded-md bg-green-50 text-green-700 border border-green-200 px-3 py-2">
            {message}
          </p>
        )}
        {error && (
          <p className="text-sm rounded-md bg-red-50 text-red-700 border border-red-200 px-3 py-2">
            {error}
          </p>
        )}
        <form onSubmit={handleAuth} className="space-y-4 py-4">
          {isSignup && (
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="firstName">First Name</Label>
                <Input
                  id="firstName"
                  value={firstName}
                  onChange={(e) => setFirstName(e.target.value)}
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="lastName">Last Name</Label>
                <Input
                  id="lastName"
                  value={lastName}
                  onChange={(e) => setLastName(e.target.value)}
                  required
                />
              </div>
            </div>
          )}
          <div className="space-y-2">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          {isSignup && (
            <div className="space-y-2">
              <Label htmlFor="phoneNumber">Phone Number</Label>
              <Input
                id="phoneNumber"
                type="tel"
                value={phoneNumber}
                onChange={(e) => setPhoneNumber(e.target.value)}
                required
              />
            </div>
          )}
          <Button
            type="submit"
            className="w-full bg-blue-600 text-white"
            variant="outline"
          >
            {isSignup ? "Sign Up" : "Login"}
          </Button>
        </form>
        <div className="text-center text-sm">
          {isSignup ? (
            <>
              Already have an account?{" "}
              <Button
                variant="link"
                className="p-0 text-blue-600"
                onClick={() => switchMode(false)}
              >
                Login
              </Button>
            </>
          ) : (
            <>
              Don't have an account?{" "}
              <Button
                variant="link"
                className="p-0 text-blue-600"
                onClick={() => switchMode(true)}
              >
                Sign Up
              </Button>
            </>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
};

export default SignupDialog;
