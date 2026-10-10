package com.zcshou.gogogo;

import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps imported/manual route points in process memory across Activity
 * recreation (for example device rotation), without stuffing up to 5000
 * points into the Android saved-state Bundle / Binder.
 *
 * This is not durable storage: process death requires route re-import.
 */
public final class LabMapRouteSession extends ViewModel {
    public final List<RouteFileParser.RoutePoint> points = new ArrayList<>();
}
