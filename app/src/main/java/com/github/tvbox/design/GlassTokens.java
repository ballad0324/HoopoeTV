package com.github.tvbox.design;

/**
 * HoopoeTV 明亮 / 深色双模式磨砂 UI Design Tokens (v2)
 *
 * 来源：hoopoe-glass-ui/index.json (schema: hoopoe.ui.direction-index/v2)
 * 生成日期：2026-11-13
 *
 * 两套模式：
 *   Light  A 极简白 / B 冷蓝玻璃（推荐） / C 暖杏磨砂
 *   Dark   A 深墨黑 / B 深蓝夜玻璃（推荐） / C 暖暗棕
 *
 * 颜色均为标准 ARGB int，可直接用于 setBackgroundColor、ColorStateList、ColorFilter。
 *
 * 用法：
 * <pre>
 *   // 静态取用
 *   view.setBackgroundColor(GlassTokens.Dark.MidnightBlue.SURFACE);
 *
 *   // 跟随主题切换
 *   GlassTokens.Light.Direction d = isDark
 *       ? GlassTokens.Dark.Direction.B
 *       : GlassTokens.Light.Direction.B;
 *   int surface = isDark
 *       ? GlassTokens.Dark.surface(d)
 *       : GlassTokens.Light.surface(d);
 * </pre>
 *
 * 硬约束：
 *   亮色 surface alpha 0.50~0.62（上限 0.65），sigma <= 30；
 *   深色 surface alpha 0.08~0.14（上限 0.18），sigma <= 24，且底色不得低于 #0C0C0F。
 */
public final class GlassTokens {

    private GlassTokens() {
    }

    // ======================= 形状参数（两模式共用） =======================

    /** 卡片圆角 */
    public static final float CARD_RADIUS_DP = 20f;
    /** 胶囊 / 按钮圆角 */
    public static final float PILL_RADIUS_DP = 999f;
    /** 描边宽度 */
    public static final float BORDER_WIDTH_DP = 1f;
    /** 投影模糊半径 */
    public static final float SHADOW_BLUR_DP = 24f;
    /** 投影 Y 偏移 */
    public static final float SHADOW_OFFSET_Y_DP = 8f;

    // ======================= 材质参数 =======================

    public static final class Material {

        private Material() {
        }

        public static final class Light {
            private Light() {
            }

            /** 模糊半径 */
            public static final float BLUR_SIGMA = 24f;
            /** 硬上限 */
            public static final float BLUR_SIGMA_HARD_MAX = 30f;
            public static final float SURFACE_ALPHA_MIN = 0.50f;
            public static final float SURFACE_ALPHA_MAX = 0.62f;
            public static final float SURFACE_ALPHA_HARD_MAX = 0.65f;
            /** 背景虚化海报墙不透明度 */
            public static final float POSTER_WALL_OPACITY = 0.25f;
            /** 海报 scrim（亮色不需要） */
            public static final float POSTER_SCRIM_MIN = 0.0f;
            public static final float POSTER_SCRIM_MAX = 0.0f;
        }

        public static final class Dark {
            private Dark() {
            }

            /** 模糊半径，深色下模糊不明显，降下来省 GPU */
            public static final float BLUR_SIGMA = 20f;
            /** 硬上限 */
            public static final float BLUR_SIGMA_HARD_MAX = 24f;
            public static final float SURFACE_ALPHA_MIN = 0.08f;
            public static final float SURFACE_ALPHA_MAX = 0.14f;
            public static final float SURFACE_ALPHA_HARD_MAX = 0.18f;
            /** 背景虚化海报墙不透明度 */
            public static final float POSTER_WALL_OPACITY = 0.35f;
            /** 深色模式必须给海报压暗，避免深底上过亮 */
            public static final float POSTER_SCRIM_MIN = 0.20f;
            public static final float POSTER_SCRIM_MAX = 0.30f;
        }
    }

    // ======================= 明亮模式 =======================

    public static final class Light {

        private Light() {
        }

        public enum Direction {
            /** A 极简白 */
            A,
            /** B 冷蓝玻璃（推荐） */
            B,
            /** C 暖杏磨砂 */
            C
        }

        /** 推荐方向 */
        public static final Direction RECOMMENDED = Direction.B;

        public static final class MinimalWhite {
            private MinimalWhite() {
            }

            public static final int BG_GRADIENT_START = 0xFFF7F6F3;
            public static final int BG_GRADIENT_END = 0xFFEDEBE7;
            /** #FFFFFF 55% */
            public static final int SURFACE = 0x8CFFFFFF;
            /** #FFFFFF 65% */
            public static final int BORDER = 0xA6FFFFFF;
            /** #1A1A1A 87% */
            public static final int TEXT_PRIMARY = 0xDE1A1A1A;
            /** #6B6B6B 60% */
            public static final int TEXT_SECONDARY = 0x996B6B6B;
            public static final int ACCENT = 0xFF2F6BFF;
            /** 投影 6% */
            public static final int SHADOW = 0x0F000000;

            public static final float SURFACE_ALPHA = 0.55f;
            public static final float BORDER_ALPHA = 0.65f;
            public static final float TEXT_PRIMARY_ALPHA = 0.87f;
            public static final float TEXT_SECONDARY_ALPHA = 0.60f;
            public static final float SHADOW_ALPHA = 0.06f;
        }

        public static final class CoolBlue {
            private CoolBlue() {
            }

            public static final int BG_GRADIENT_START = 0xFFEAF2FA;
            public static final int BG_GRADIENT_END = 0xFFD8E6F2;
            /** #FFFFFF 60% */
            public static final int SURFACE = 0x99FFFFFF;
            /** #FFFFFF 70% */
            public static final int BORDER = 0xB3FFFFFF;
            /** #16181D 88% */
            public static final int TEXT_PRIMARY = 0xE016181D;
            /** #5B6573 62% */
            public static final int TEXT_SECONDARY = 0x9E5B6573;
            public static final int ACCENT = 0xFF3A7BD5;
            /** 暖强调色，用于评分/角标 */
            public static final int ACCENT_WARM = 0xFFE0603A;
            /** 投影 7% */
            public static final int SHADOW = 0x12000000;

            public static final float SURFACE_ALPHA = 0.60f;
            public static final float BORDER_ALPHA = 0.70f;
            public static final float TEXT_PRIMARY_ALPHA = 0.88f;
            public static final float TEXT_SECONDARY_ALPHA = 0.62f;
            public static final float SHADOW_ALPHA = 0.07f;
        }

        public static final class WarmApricot {
            private WarmApricot() {
            }

            public static final int BG_GRADIENT_START = 0xFFFBF1E6;
            public static final int BG_GRADIENT_END = 0xFFF3E3D3;
            /** #FFFFFF 58% */
            public static final int SURFACE = 0x94FFFFFF;
            /** #FFFFFF 65% */
            public static final int BORDER = 0xA6FFFFFF;
            /** #2A2018 88% */
            public static final int TEXT_PRIMARY = 0xE02A2018;
            /** #6E5A48 62% */
            public static final int TEXT_SECONDARY = 0x9E6E5A48;
            public static final int ACCENT = 0xFFD97757;
            /** #3A2A1A 7% */
            public static final int SHADOW = 0x123A2A1A;

            public static final float SURFACE_ALPHA = 0.58f;
            public static final float BORDER_ALPHA = 0.65f;
            public static final float TEXT_PRIMARY_ALPHA = 0.88f;
            public static final float TEXT_SECONDARY_ALPHA = 0.62f;
            public static final float SHADOW_ALPHA = 0.07f;
        }

        public static int bgGradientStart(Direction d) {
            switch (d) {
                case A:
                    return MinimalWhite.BG_GRADIENT_START;
                case C:
                    return WarmApricot.BG_GRADIENT_START;
                case B:
                default:
                    return CoolBlue.BG_GRADIENT_START;
            }
        }

        public static int bgGradientEnd(Direction d) {
            switch (d) {
                case A:
                    return MinimalWhite.BG_GRADIENT_END;
                case C:
                    return WarmApricot.BG_GRADIENT_END;
                case B:
                default:
                    return CoolBlue.BG_GRADIENT_END;
            }
        }

        public static int surface(Direction d) {
            switch (d) {
                case A:
                    return MinimalWhite.SURFACE;
                case C:
                    return WarmApricot.SURFACE;
                case B:
                default:
                    return CoolBlue.SURFACE;
            }
        }

        public static int border(Direction d) {
            switch (d) {
                case A:
                    return MinimalWhite.BORDER;
                case C:
                    return WarmApricot.BORDER;
                case B:
                default:
                    return CoolBlue.BORDER;
            }
        }

        public static int textPrimary(Direction d) {
            switch (d) {
                case A:
                    return MinimalWhite.TEXT_PRIMARY;
                case C:
                    return WarmApricot.TEXT_PRIMARY;
                case B:
                default:
                    return CoolBlue.TEXT_PRIMARY;
            }
        }

        public static int textSecondary(Direction d) {
            switch (d) {
                case A:
                    return MinimalWhite.TEXT_SECONDARY;
                case C:
                    return WarmApricot.TEXT_SECONDARY;
                case B:
                default:
                    return CoolBlue.TEXT_SECONDARY;
            }
        }

        public static int accent(Direction d) {
            switch (d) {
                case A:
                    return MinimalWhite.ACCENT;
                case C:
                    return WarmApricot.ACCENT;
                case B:
                default:
                    return CoolBlue.ACCENT;
            }
        }

        public static int shadow(Direction d) {
            switch (d) {
                case A:
                    return MinimalWhite.SHADOW;
                case C:
                    return WarmApricot.SHADOW;
                case B:
                default:
                    return CoolBlue.SHADOW;
            }
        }
    }

    // ======================= 深色模式 =======================

    public static final class Dark {

        private Dark() {
        }

        public enum Direction {
            /** A 深墨黑 */
            A,
            /** B 深蓝夜玻璃（推荐） */
            B,
            /** C 暖暗棕 */
            C
        }

        /** 推荐方向 */
        public static final Direction RECOMMENDED = Direction.B;

        /** 页面底色下限，低于此值磨砂会失效 */
        public static final int BASE_MIN = 0xFF0C0C0F;

        public static final class InkBlack {
            private InkBlack() {
            }

            public static final int BG_GRADIENT_START = 0xFF101013;
            public static final int BG_GRADIENT_END = 0xFF17171B;
            /** #FFFFFF 10% */
            public static final int SURFACE = 0x1AFFFFFF;
            /** #FFFFFF 16% */
            public static final int BORDER = 0x29FFFFFF;
            /** #F2F2F5 92% */
            public static final int TEXT_PRIMARY = 0xEBF2F2F5;
            /** #9A9AA2 60% */
            public static final int TEXT_SECONDARY = 0x999A9AA2;
            public static final int ACCENT = 0xFF5B8DEF;
            /** 30%，深色下实际可不用 */
            public static final int SHADOW = 0x4D000000;

            public static final float SURFACE_ALPHA = 0.10f;
            public static final float BORDER_ALPHA = 0.16f;
            public static final float TEXT_PRIMARY_ALPHA = 0.92f;
            public static final float TEXT_SECONDARY_ALPHA = 0.60f;
            public static final float SHADOW_ALPHA = 0.30f;
        }

        public static final class MidnightBlue {
            private MidnightBlue() {
            }

            public static final int BG_GRADIENT_START = 0xFF0C1520;
            public static final int BG_GRADIENT_END = 0xFF14243A;
            /** #FFFFFF 11% */
            public static final int SURFACE = 0x1CFFFFFF;
            /** #FFFFFF 18% */
            public static final int BORDER = 0x2EFFFFFF;
            /** #EAF1FA 90% */
            public static final int TEXT_PRIMARY = 0xE6EAF1FA;
            /** #93A4B8 60% */
            public static final int TEXT_SECONDARY = 0x9993A4B8;
            public static final int ACCENT = 0xFF4C9AFF;
            /** 暖强调色，评分角标用，避免整屏发蓝 */
            public static final int ACCENT_WARM = 0xFFFF8A5C;
            /** 30%，深色下实际可不用 */
            public static final int SHADOW = 0x4D000000;

            public static final float SURFACE_ALPHA = 0.11f;
            public static final float BORDER_ALPHA = 0.18f;
            public static final float TEXT_PRIMARY_ALPHA = 0.90f;
            public static final float TEXT_SECONDARY_ALPHA = 0.60f;
            public static final float SHADOW_ALPHA = 0.30f;
        }

        public static final class EmberBrown {
            private EmberBrown() {
            }

            public static final int BG_GRADIENT_START = 0xFF16110D;
            public static final int BG_GRADIENT_END = 0xFF221A14;
            /** #FFFFFF 10% */
            public static final int SURFACE = 0x1AFFFFFF;
            /** #FFE0C2 14%，暖白高光 */
            public static final int BORDER = 0x24FFE0C2;
            /** #F5EDE4 90% */
            public static final int TEXT_PRIMARY = 0xE6F5EDE4;
            /** #A89484 60% */
            public static final int TEXT_SECONDARY = 0x99A89484;
            public static final int ACCENT = 0xFFE88B63;
            /** 30%，深色下实际可不用 */
            public static final int SHADOW = 0x4D000000;

            public static final float SURFACE_ALPHA = 0.10f;
            public static final float BORDER_ALPHA = 0.14f;
            public static final float TEXT_PRIMARY_ALPHA = 0.90f;
            public static final float TEXT_SECONDARY_ALPHA = 0.60f;
            public static final float SHADOW_ALPHA = 0.30f;
        }

        public static int bgGradientStart(Direction d) {
            switch (d) {
                case A:
                    return InkBlack.BG_GRADIENT_START;
                case C:
                    return EmberBrown.BG_GRADIENT_START;
                case B:
                default:
                    return MidnightBlue.BG_GRADIENT_START;
            }
        }

        public static int bgGradientEnd(Direction d) {
            switch (d) {
                case A:
                    return InkBlack.BG_GRADIENT_END;
                case C:
                    return EmberBrown.BG_GRADIENT_END;
                case B:
                default:
                    return MidnightBlue.BG_GRADIENT_END;
            }
        }

        public static int surface(Direction d) {
            switch (d) {
                case A:
                    return InkBlack.SURFACE;
                case C:
                    return EmberBrown.SURFACE;
                case B:
                default:
                    return MidnightBlue.SURFACE;
            }
        }

        public static int border(Direction d) {
            switch (d) {
                case A:
                    return InkBlack.BORDER;
                case C:
                    return EmberBrown.BORDER;
                case B:
                default:
                    return MidnightBlue.BORDER;
            }
        }

        public static int textPrimary(Direction d) {
            switch (d) {
                case A:
                    return InkBlack.TEXT_PRIMARY;
                case C:
                    return EmberBrown.TEXT_PRIMARY;
                case B:
                default:
                    return MidnightBlue.TEXT_PRIMARY;
            }
        }

        public static int textSecondary(Direction d) {
            switch (d) {
                case A:
                    return InkBlack.TEXT_SECONDARY;
                case C:
                    return EmberBrown.TEXT_SECONDARY;
                case B:
                default:
                    return MidnightBlue.TEXT_SECONDARY;
            }
        }

        public static int accent(Direction d) {
            switch (d) {
                case A:
                    return InkBlack.ACCENT;
                case C:
                    return EmberBrown.ACCENT;
                case B:
                default:
                    return MidnightBlue.ACCENT;
            }
        }

        public static int shadow(Direction d) {
            switch (d) {
                case A:
                    return InkBlack.SHADOW;
                case C:
                    return EmberBrown.SHADOW;
                case B:
                default:
                    return MidnightBlue.SHADOW;
            }
        }
    }

    // ======================= 跨模式便捷方法 =======================

    /** 根据是否深色取推荐方向对应的表面色 */
    public static int recommendedSurface(boolean isDark) {
        return isDark
                ? Dark.surface(Dark.RECOMMENDED)
                : Light.surface(Light.RECOMMENDED);
    }

    /** 根据是否深色取推荐方向的描边色 */
    public static int recommendedBorder(boolean isDark) {
        return isDark
                ? Dark.border(Dark.RECOMMENDED)
                : Light.border(Light.RECOMMENDED);
    }

    /** 根据是否深色取主文字色 */
    public static int recommendedTextPrimary(boolean isDark) {
        return isDark
                ? Dark.textPrimary(Dark.RECOMMENDED)
                : Light.textPrimary(Light.RECOMMENDED);
    }
}
