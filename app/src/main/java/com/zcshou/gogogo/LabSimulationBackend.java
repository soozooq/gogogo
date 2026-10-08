package com.zcshou.gogogo;

/**
 * Lab 21 common source contract for synthetic location generators.
 *
 * Backends generate samples only. Publication to Android providers remains centralized
 * in ServiceGo so provider lifecycle, policy, diagnostics and mock provenance stay in
 * one place.
 */
public interface LabSimulationBackend {
    String id();
    boolean isActive();
    LabSimulationSample snapshot();
}
