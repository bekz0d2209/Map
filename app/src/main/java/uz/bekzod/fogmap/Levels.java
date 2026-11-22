package uz.bekzod.fogmap;

/** Levels for opened area, compared with real places of known size. */
public final class Levels {
    private Levels() {}

    public static final String[] NAMES = {
            "Первые шаги",
            "Футбольное поле",
            "10 футбольных полей",
            "Ичан-Кала в Хиве",
            "Ватикан",
            "Монако",
            "Центральный парк Нью-Йорка",
            "Гибралтар",
            "Сан-Марино",
            "Лихтенштейн",
            "Мальта",
            "Андорра",
    };

    /** Area in m² needed for each level. */
    public static final double[] AREA = {
            0,
            7_140,          // FIFA pitch 105 × 68 m
            71_400,
            260_000,        // Itchan Kala ≈ 26 ha
            440_000,        // Vatican ≈ 0.44 km²
            2_020_000,      // Monaco ≈ 2.02 km²
            3_410_000,      // Central Park ≈ 3.41 km²
            6_700_000,      // Gibraltar ≈ 6.7 km²
            61_000_000,     // San Marino ≈ 61 km²
            160_000_000,    // Liechtenstein ≈ 160 km²
            316_000_000,    // Malta ≈ 316 km²
            468_000_000,    // Andorra ≈ 468 km²
    };

    public static int level(double area) {
        int l = 0;
        for (int i = 0; i < AREA.length; i++) if (area >= AREA[i]) l = i;
        return l;
    }

    /** 0..1 progress towards the next level. */
    public static float progress(double area) {
        int l = level(area);
        if (l >= AREA.length - 1) return 1f;
        return (float) ((area - AREA[l]) / (AREA[l + 1] - AREA[l]));
    }
}
