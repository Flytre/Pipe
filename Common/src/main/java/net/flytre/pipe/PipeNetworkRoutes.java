package net.flytre.pipe;

import net.flytre.flytre_lib.api.storage.inventory.filter.FilterInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;


/**
 * Stores the structure of all possible routes through a network from a starting point in it
 */
public interface PipeNetworkRoutes {

    boolean isTerminal();

    void addTerminal(@NotNull StackFreePath path);

    PipeNetworkRoutes addNonTerminal(Supplier<FilterInventory> filter, Supplier<Boolean> roundRobin);

    List<StackFreePath> getPathsFor(ItemStack stack, TriFunction<ItemStack, BlockPos, Direction, Boolean> insertionCheck);

    class RecursiveNode implements PipeNetworkRoutes {
        private final List<PipeNetworkRoutes> entries = new ArrayList<>();
        private final Supplier<FilterInventory> filter;
        private final Supplier<Boolean> roundRobin;

        public RecursiveNode(Supplier<FilterInventory> filter, Supplier<Boolean> roundRobin) {
            this.filter = filter;
            this.roundRobin = roundRobin;
        }

        @Override
        public boolean isTerminal() {
            return false;
        }

        @Override
        public void addTerminal(@NotNull StackFreePath path) {
            TerminalNode result = new TerminalNode(path);
            entries.add(result);
        }

        @Override
        public PipeNetworkRoutes addNonTerminal(Supplier<FilterInventory> filter, Supplier<Boolean> roundRobin) {
            PipeNetworkRoutes result = new RecursiveNode(filter, roundRobin);
            entries.add(result);
            return result;
        }

        @Override
        public List<StackFreePath> getPathsFor(ItemStack stack, TriFunction<ItemStack, BlockPos, Direction, Boolean> insertionCheck) {
            FilterInventory filterInv = filter.get();
            if (!filterInv.isEmpty() && !filterInv.passFilterTest(stack))
                return List.of();

            //since it's a bfs, closer entries have been entered first
            if (!roundRobin.get()) {
                for (var entry : entries) {
                    List<StackFreePath> paths = entry.getPathsFor(stack, insertionCheck);
                    if (paths.size() > 0)
                        return paths;
                }
                return List.of();
            } else {

                List<StackFreePath> paths = new ArrayList<>();
                for (var entry : entries) {
                    paths.addAll(entry.getPathsFor(stack, insertionCheck));
                }
                return paths;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RecursiveNode that = (RecursiveNode) o;

            if (!entries.equals(that.entries)) return false;
            if (!Objects.equals(filter, that.filter)) return false;
            return Objects.equals(roundRobin, that.roundRobin);
        }

        @Override
        public int hashCode() {
            int result = entries.hashCode();
            result = 31 * result + (filter != null ? filter.hashCode() : 0);
            result = 31 * result + (roundRobin != null ? roundRobin.hashCode() : 0);
            return result;
        }
    }

    class TerminalNode implements PipeNetworkRoutes {
        private final @NotNull StackFreePath path;

        public TerminalNode(@NotNull StackFreePath path) {
            this.path = path;
        }

        @Override
        public boolean isTerminal() {
            return true;
        }

        @Override
        public void addTerminal(@NotNull StackFreePath path) {
            throw new AssertionError("Cannot add to terminal node");
        }

        @Override
        public PipeNetworkRoutes addNonTerminal(Supplier<FilterInventory> filter, Supplier<Boolean> roundRobin) {
            throw new AssertionError("Cannot add to terminal node");
        }

        @Override
        public List<StackFreePath> getPathsFor(ItemStack stack, TriFunction<ItemStack, BlockPos, Direction, Boolean> insertionCheck) {
            return insertionCheck.apply(stack, path.getDestination(), path.getDirection()) ? List.of(path) : List.of();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TerminalNode that = (TerminalNode) o;

            return path.equals(that.path);
        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }
    }
}
