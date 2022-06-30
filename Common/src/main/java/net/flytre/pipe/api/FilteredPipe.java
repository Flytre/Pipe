package net.flytre.pipe.api;

/**
 * Filtered pipes only allow resources under certain conditions. For example, fluid pipes only accept fluids if they're empty
 * or the current fluid is the same as the fluid that is trying to enter.
 */
public interface FilteredPipe {

    boolean canAccept(Object resource);
}
