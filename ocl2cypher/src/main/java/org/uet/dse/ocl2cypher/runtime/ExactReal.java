package org.uet.dse.ocl2cypher.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/** Exact rational subcarrier of mathematical Real; no rounding or infinities. */
public record ExactReal(BigInteger numerator, BigInteger denominator)
        implements Comparable<ExactReal> {
    public static final ExactReal ZERO = of(BigInteger.ZERO);
    public ExactReal {
        Objects.requireNonNull(numerator);
        Objects.requireNonNull(denominator);
        if (denominator.signum() == 0) throw new ArithmeticException("zero denominator");
        if (denominator.signum() < 0) { numerator = numerator.negate(); denominator = denominator.negate(); }
        BigInteger gcd = numerator.gcd(denominator);
        numerator = numerator.divide(gcd);
        denominator = denominator.divide(gcd);
    }
    public static ExactReal of(BigInteger n) { return new ExactReal(n, BigInteger.ONE); }
    public static ExactReal of(BigDecimal n) {
        int scale = n.scale();
        return scale >= 0 ? new ExactReal(n.unscaledValue(), BigInteger.TEN.pow(scale))
                : of(n.unscaledValue().multiply(BigInteger.TEN.pow(Math.negateExact(scale))));
    }
    public ExactReal add(ExactReal b) { return new ExactReal(numerator.multiply(b.denominator).add(b.numerator.multiply(denominator)), denominator.multiply(b.denominator)); }
    public ExactReal subtract(ExactReal b) { return add(b.negate()); }
    public ExactReal multiply(ExactReal b) { return new ExactReal(numerator.multiply(b.numerator), denominator.multiply(b.denominator)); }
    public ExactReal divide(ExactReal b) { return new ExactReal(numerator.multiply(b.denominator), denominator.multiply(b.numerator)); }
    public ExactReal negate() { return new ExactReal(numerator.negate(), denominator); }
    public int signum() { return numerator.signum(); }
    public BigInteger floor() {
        BigInteger[] qr = numerator.divideAndRemainder(denominator);
        return numerator.signum() < 0 && qr[1].signum() != 0 ? qr[0].subtract(BigInteger.ONE) : qr[0];
    }
    public BigInteger round() { return add(new ExactReal(BigInteger.ONE, BigInteger.TWO)).floor(); }
    @Override public int compareTo(ExactReal b) { return numerator.multiply(b.denominator).compareTo(b.numerator.multiply(denominator)); }
    /** Storage boundary only: throws for non-terminating decimal, never rounds. */
    public BigDecimal toBigDecimalExact() { return new BigDecimal(numerator).divide(new BigDecimal(denominator)); }
    @Override public String toString() {
        try { return toBigDecimalExact().toPlainString(); }
        catch (ArithmeticException e) { return numerator + "/" + denominator; }
    }
}
