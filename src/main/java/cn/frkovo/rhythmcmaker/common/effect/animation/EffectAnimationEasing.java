package cn.frkovo.rhythmcmaker.common.effect.animation;

/** Easing functions shared by chart tracks and effect animation channels. */
public final class EffectAnimationEasing {
    private EffectAnimationEasing() {
    }

    public static double apply(double progress, int easingId) {
        double value = Double.isFinite(progress) ? Math.max(0.0, Math.min(1.0, progress)) : 0.0;
        return switch (Math.max(0, Math.min(33, easingId))) {
            case 1 -> 1 - Math.cos(value * Math.PI) / 2;
            case 2 -> Math.sin(value * Math.PI) / 2;
            case 3 -> -(Math.cos(Math.PI * value) - 1) / 2;
            case 4 -> value * value;
            case 5 -> 1 - Math.pow(1 - value, 2);
            case 6 -> value < 0.5 ? 2 * value * value : 1 - Math.pow(-2 * value + 2, 2) / 2;
            case 7 -> value * value * value;
            case 8 -> 1 - Math.pow(1 - value, 3);
            case 9 -> value < 0.5 ? 4 * value * value * value : 1 - Math.pow(-2 * value + 2, 3) / 2;
            case 10 -> Math.pow(value, 4);
            case 11 -> 1 - Math.pow(1 - value, 4);
            case 12 -> value < 0.5 ? 8 * Math.pow(value, 4) : 1 - Math.pow(-2 * value + 2, 4) / 2;
            case 13 -> Math.pow(value, 5);
            case 14 -> 1 - Math.pow(1 - value, 5);
            case 15 -> value < 0.5 ? 16 * Math.pow(value, 5) : 1 - Math.pow(-2 * value + 2, 5) / 2;
            case 16 -> value == 0 ? 0 : Math.pow(2, 10 * value - 10);
            case 17 -> value == 1 ? 1 : 1 - Math.pow(2, -10 * value);
            case 18 -> value == 0 ? 0 : value == 1 ? 1 : value < 0.5 ? Math.pow(2, 20 * value - 10) / 2 : (2 - Math.pow(2, -20 * value + 10)) / 2;
            case 19 -> 1 - Math.sqrt(1 - value * value);
            case 20 -> Math.sqrt(1 - Math.pow(value - 1, 2));
            case 21 -> value < 0.5 ? (1 - Math.sqrt(1 - Math.pow(2 * value, 2))) / 2 : (Math.sqrt(1 - Math.pow(-2 * value + 2, 2)) + 1) / 2;
            case 22 -> 2.70158 * value * value * value - 1.70158 * value * value;
            case 23 -> 1 + 2.70158 * Math.pow(value - 1, 3) + 1.70158 * Math.pow(value - 1, 2);
            case 24 -> value < 0.5 ? (Math.pow(2 * value, 2) * ((1.70158 * 1.525 + 1) * 2 * value - 1.70158 * 1.525)) / 2 : (Math.pow(2 * value - 2, 2) * ((1.70158 * 1.525 + 1) * (value * 2 - 2) + 1.70158 * 1.525) + 2) / 2;
            case 25 -> value == 0 || value == 1 ? value : -Math.pow(2, 10 * value - 10) * Math.sin((value * 10 - 10.75) * (2 * Math.PI / 3));
            case 26 -> value == 0 || value == 1 ? value : Math.pow(2, -10 * value) * Math.sin((value * 10 - 0.75) * (2 * Math.PI / 3)) + 1;
            case 27 -> value == 0 || value == 1 ? value : value < 0.5 ? -(Math.pow(2, 20 * value - 10) * Math.sin((20 * value - 11.125) * (2 * Math.PI / 4.5))) / 2 : Math.pow(2, -20 * value + 10) * Math.sin((20 * value - 11.125) * (2 * Math.PI / 4.5)) / 2 + 1;
            case 28 -> 1 - bounce(1 - value);
            case 29 -> bounce(value);
            case 30 -> value < 0.5 ? (1 - bounce(1 - 2 * value)) / 2 : (1 + bounce(2 * value - 1)) / 2;
            case 31 -> 0;
            case 32 -> 1;
            case 33 -> value < 0.5 ? 0 : 1;
            default -> value;
        };
    }

    public static int normalizeId(int easingId) {
        return Math.max(0, Math.min(33, easingId));
    }

    private static double bounce(double value) {
        if (value < 1 / 2.75) return 7.5625 * value * value;
        if (value < 2 / 2.75) return 7.5625 * Math.pow(value - 1.5 / 2.75, 2) + 0.75;
        if (value < 2.5 / 2.75) return 7.5625 * Math.pow(value - 2.25 / 2.75, 2) + 0.9375;
        return 7.5625 * Math.pow(value - 2.625 / 2.75, 2) + 0.984375;
    }
}
