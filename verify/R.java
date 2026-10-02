package com.bugtraqapps.bugdeck;

/**
 * Generated at verify time from the res/ files. Only the ids the Java sources
 * actually reference, so a reference to a removed view fails the build here
 * rather than as a NullPointerException on a user's phone.
 */
public final class R {
    private R() {}

    public static final class layout {
        public static final int activity_main      = 1;
        public static final int fragment_scanner   = 2;
        public static final int fragment_hackbar   = 3;
        public static final int fragment_dork      = 4;
        public static final int item_result        = 5;
        public static final int item_dork_result   = 6;
        public static final int dialog_settings    = 7;
    }

    public static final class id {
        public static final int toolbar           = 100;
        public static final int tabs              = 101;
        public static final int pager             = 102;
        // scanner
        public static final int modeGroup         = 110;
        public static final int modeSqli          = 111;
        public static final int modeAdmin         = 112;
        public static final int target            = 113;
        public static final int crawlBox          = 114;
        public static final int depth             = 115;
        public static final int wordlistSpinner   = 116;
        public static final int scan              = 117;
        public static final int stop              = 118;
        public static final int progress          = 119;
        public static final int status            = 120;
        public static final int results           = 121;
        public static final int targetLayout      = 122;
        public static final int depthLayout       = 123;
        public static final int wordlistRow       = 124;
        public static final int depthLabel        = 125;
        // result row
        public static final int result_url        = 130;
        public static final int result_verdict    = 131;
        public static final int result_evidence   = 132;
        public static final int result_status     = 133;
        // hackbar
        public static final int hackbar_target    = 140;
        public static final int hackbar_category  = 141;
        public static final int hackbar_payload   = 142;
        public static final int hackbar_send      = 143;
        public static final int hackbar_copy      = 144;
        public static final int hackbar_generated = 145;
        public static final int hackbar_response  = 146;
        // dork
        public static final int dork_query        = 150;
        public static final int dork_domain       = 151;
        public static final int dork_provider     = 152;
        public static final int dork_key          = 153;
        public static final int dork_key_label    = 154;
        public static final int dork_cx           = 155;
        public static final int dork_cx_label     = 156;
        public static final int dork_pages        = 157;
        public static final int dork_pages_value  = 158;
        public static final int dork_search       = 159;
        public static final int dork_clear        = 160;
        public static final int dork_progress     = 161;
        public static final int dork_status       = 162;
        public static final int dork_results      = 163;
        public static final int dork_result_url   = 164;
        public static final int dorkKeyRow        = 165;
        public static final int dorkKeyLayout     = 166;
        public static final int dorkCxLayout      = 167;
        // settings dialog
        public static final int set_brave         = 170;
        public static final int set_google        = 171;
        public static final int set_cx            = 172;
        public static final int set_ua            = 173;
        // menu
        public static final int action_settings   = 180;
    }

    public static final class string {
        public static final int app_name              = 200;
        public static final int tab_scanner           = 201;
        public static final int tab_hackbar           = 202;
        public static final int tab_dork              = 203;
        public static final int mode_sqli             = 204;
        public static final int mode_admin            = 205;
        public static final int source_crawl          = 206;
        public static final int label_target          = 207;
        public static final int label_depth           = 208;
        public static final int label_wordlist        = 209;
        public static final int btn_scan              = 210;
        public static final int btn_stop              = 211;
        public static final int hint_target           = 212;
        public static final int label_dork_query      = 213;
        public static final int label_dork_domain     = 214;
        public static final int label_provider        = 215;
        public static final int label_dork_key        = 216;
        public static final int label_dork_cx         = 217;
        public static final int btn_search            = 218;
        public static final int btn_clear             = 219;
        public static final int hint_dork             = 220;
        public static final int hint_dork_domain      = 221;
        public static final int hint_dork_key         = 222;
        public static final int hint_dork_cx          = 223;
        public static final int pages_fmt_default     = 224;
        public static final int pages_fmt             = 225;
        public static final int label_hackbar_target  = 226;
        public static final int label_hackbar_category = 227;
        public static final int label_hackbar_payload = 228;
        public static final int label_generated       = 229;
        public static final int label_response        = 230;
        public static final int btn_send              = 231;
        public static final int btn_copy              = 232;
        public static final int settings              = 233;
        public static final int settings_brave        = 234;
        public static final int settings_google       = 235;
        public static final int settings_google_hint  = 236;
        public static final int settings_cx           = 237;
        public static final int settings_ua           = 238;
        public static final int settings_ua_hint      = 239;
        public static final int settings_note         = 240;
        public static final int v_vulnerable          = 241;
        public static final int v_not_vulnerable      = 242;
        public static final int v_blocked             = 243;
        public static final int v_skipped             = 244;
        public static final int v_error               = 245;
        public static final int no_evidence           = 246;
        public static final int copied                = 247;        public static final int status_fmt            = 248;
        public static final int status_base_fmt       = 249;
        public static final int need_target           = 250;
        public static final int starting              = 251;
        public static final int stopping              = 252;
        public static final int progress_fmt          = 253;
        public static final int crawled               = 254;
        public static final int done_fmt              = 255;
        public static final int scan_error            = 256;
        public static final int wordlist_missing      = 257;
        public static final int wordlist_empty        = 258;
        public static final int dork_need_query       = 259;
        public static final int dork_need_cx          = 260;
        public static final int dork_searching        = 261;
        public static final int dork_found            = 262;
        public static final int dork_failed           = 263;
        public static final int dork_key_missing      = 264;
        public static final int payloads_missing      = 265;
        public static final int need_generated_url    = 266;
        public static final int sending               = 267;
        public static final int fetch_failed          = 268;
        public static final int redirected_fmt        = 269;
    }

    public static final class menu {
        public static final int menu_main = 400;
    }

    public static final class color {
        public static final int verdict_vulnerable    = 300;
        public static final int verdict_vulnerable_bg = 301;
        public static final int verdict_clean         = 302;
        public static final int verdict_clean_bg      = 303;
        public static final int verdict_blocked       = 304;
        public static final int verdict_blocked_bg    = 305;
        public static final int verdict_skipped       = 306;
        public static final int verdict_skipped_bg    = 307;
    }
}
