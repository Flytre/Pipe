package net.flytre.pipe.api;

import net.flytre.flytre_lib.api.storage.inventory.filter.ResourceFilter;
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
interface PipeNetworkRoutes<T> {

    boolean isTerminal();

    void addTerminal(@NotNull PipePath.Potential<T> path);

    PipeNetworkRoutes<T> addNonTerminal(Supplier<ResourceFilter<? super T>> filter, Supplier<Boolean> roundRobin);

    List<PipePath.Potential<T>> getPathsFor(T resource, TriFunction<T, BlockPos, Direction, Boolean> insertionCheck);

    final class RecursiveNode<T> implements PipeNetworkRoutes<T> {
        private final List<PipeNetworkRoutes<T>> entries = new ArrayList<>();
        private final Supplier<ResourceFilter<? super T>> filter;
        private final Supplier<Boolean> roundRobin;

        public RecursiveNode(Supplier<ResourceFilter<? super T>> filter, Supplier<Boolean> roundRobin) {
            this.filter = filter;
            this.roundRobin = roundRobin;
        }

        @Override
        public boolean isTerminal() {
            return false;
        }

        @Override
        public void addTerminal(@NotNull PipePath.Potential<T> path) {
            TerminalNode<T> result = new TerminalNode<>(path);
            entries.add(result);
        }

        @Override
        public PipeNetworkRoutes<T> addNonTerminal(Supplier<ResourceFilter<? super T>> filter, Supplier<Boolean> roundRobin) {
            PipeNetworkRoutes<T> result = new RecursiveNode<>(filter, roundRobin);
            entries.add(result);
            return result;
        }

        @Override
        public List<PipePath.Potential<T>> getPathsFor(T resource, TriFunction<T, BlockPos, Direction, Boolean> insertionCheck) {
            ResourceFilter<? super T> filterInv = filter.get();
            if (!filterInv.isEmpty() && !filterInv.passFilterTest(resource))
                return List.of();

            //since it's a bfs, closer entries have been entered first
            if (!roundRobin.get()) {
                for (var entry : entries) {
                    List<PipePath.Potential<T>> paths = entry.getPathsFor(resource, insertionCheck);
                    if (paths.size() > 0)
                        return paths;
                }
                return List.of();
            } else {

                List<PipePath.Potential<T>> paths = new ArrayList<>();
                for (var entry : entries) {
                    paths.addAll(entry.getPathsFor(resource, insertionCheck));
                }
                return paths;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RecursiveNode<?> that = (RecursiveNode<?>) o;

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

    final class TerminalNode<T> implements PipeNetworkRoutes<T> {
        private final PipePath.@NotNull Potential<T> path;

        public TerminalNode(@NotNull PipePath.Potential<T> path) {
            this.path = path;
        }

        @Override
        public boolean isTerminal() {
            return true;
        }

        @Override
        public void addTerminal(@NotNull PipePath.Potential<T> path) {
            throw new AssertionError("Cannot add to terminal node");
        }

        @Override
        public PipeNetworkRoutes<T> addNonTerminal(Supplier<ResourceFilter<? super T>> filter, Supplier<Boolean> roundRobin) {
            throw new AssertionError("Cannot add to terminal node");
        }

        @Override
        public List<PipePath.Potential<T>> getPathsFor(T resource, TriFunction<T, BlockPos, Direction, Boolean> insertionCheck) {
            return insertionCheck.apply(resource, path.destination(), path.direction()) ? List.of(path) : List.of();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TerminalNode<?> that = (TerminalNode<?>) o;

            return path.equals(that.path);
        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }

        public PipePath.@NotNull Potential<T> getPath() {
            return path;
        }

        @Override
        public String toString() {
            return "TerminalNode[" +
                    "path=" + path + ']';
        }

    }
}
