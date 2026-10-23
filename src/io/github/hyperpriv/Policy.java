package io.github.hyperpriv;

final class Policy {
    private Policy() {}
    // PowerKeeper 4.2.00: cookie 253 is the display thermal refresh-rate branch.
    // Other display modes, kernel thermal handling and charging limits are untouched.
    static boolean isThermalDisplayCookie(int cookie) { return cookie == 253; }
}
