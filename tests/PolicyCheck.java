package io.github.hyperpriv;
public final class PolicyCheck {
 public static void main(String[] args) {
  if(!Policy.isThermalDisplayCookie(253))throw new AssertionError("thermal cookie must be handled");
  for(int cookie:new int[]{-1,0,209,244,245,247,248,249,251,252,254,262,272,Integer.MAX_VALUE})
   if(Policy.isThermalDisplayCookie(cookie))throw new AssertionError("unrelated display mode blocked: "+cookie);
  System.out.println("Display policy: thermal cookie handled; unrelated modes preserved");
 }
}
