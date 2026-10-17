package com.soozooq.tianwaitian.fixture;

import android.app.Activity;

/**
 * Manifest-only component declaration used for V2 archive inspection.
 * This Activity is NEVER started or installed by the host. All visible proxy
 * behavior is executed by the separately declared Tianwaitian host Activity.
 */
public final class GuestV2DeclaredActivity extends Activity {
}
