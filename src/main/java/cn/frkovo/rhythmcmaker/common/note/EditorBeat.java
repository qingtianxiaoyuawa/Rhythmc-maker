package cn.frkovo.rhythmcmaker.common.note;

public record EditorBeat(int whole, int numerator, int denominator) {
    public static final int MAX_DENOMINATOR = 1000;
    public static final int MAX_WHOLE = 1_000_000;

    public boolean isValid() {
        return whole >= 0 && whole <= MAX_WHOLE && denominator >= 1 && denominator <= MAX_DENOMINATOR
                && numerator >= 0 && numerator < denominator && value() <= MAX_WHOLE;
    }

    public double value() {
        return whole + numerator / (double) denominator;
    }

    public boolean represents(double beat) {
        return isValid() && Double.isFinite(beat) && Math.abs(value() - beat) < 1.0e-9;
    }

    public static EditorBeat fromValue(double beat) {
        return fromValue(beat, 1);
    }

    public static EditorBeat fromValue(double beat, int preferredDenominator) {
        if (!Double.isFinite(beat) || beat < 0.0 || beat > MAX_WHOLE) throw new IllegalArgumentException("Invalid beat");
        if (preferredDenominator >= 1 && preferredDenominator <= MAX_DENOMINATOR) {
            long preferredTick = Math.round(beat * preferredDenominator);
            double preferredBeat = preferredTick / (double) preferredDenominator;
            if (Math.abs(preferredBeat - beat) < 1.0e-9) {
                return new EditorBeat((int) (preferredTick / preferredDenominator),
                        (int) (preferredTick % preferredDenominator), preferredDenominator);
            }
        }
        int whole = (int) Math.floor(beat);
        double fraction = beat - whole;
        EditorBeat closest = new EditorBeat(whole, 0, 1);
        double error = fraction;
        for (int denominator = 1; denominator <= MAX_DENOMINATOR; denominator++) {
            int numerator = (int) Math.round(fraction * denominator);
            double candidateError = Math.abs(fraction - numerator / (double) denominator);
            if (candidateError + 1.0e-12 < error) {
                closest = numerator == denominator ? new EditorBeat(whole + 1, 0, 1)
                        : new EditorBeat(whole, numerator, denominator);
                error = candidateError;
            }
        }
        return closest;
    }
}
