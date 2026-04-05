package com.example.examplemod.marker;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ClientMarkerState {
    private static final Set<BlockPos> MARKERS = new LinkedHashSet<>();

    private ClientMarkerState() {}

    public static synchronized void setMarker(BlockPos pos, boolean enabled) {
        if (pos == null) {
            return;
        }

        BlockPos immutable = pos.immutable();
        if (enabled) {
            MARKERS.add(immutable);
        } else {
            MARKERS.remove(immutable);
        }
    }

    public static synchronized void resetAll() {
        MARKERS.clear();
    }

    public static synchronized List<BlockPos> getMarkersSnapshot() {
        return new ArrayList<>(MARKERS);
    }

    public static synchronized int count() {
        return MARKERS.size();
    }
}
