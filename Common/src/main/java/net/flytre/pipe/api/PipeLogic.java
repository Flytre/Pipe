package net.flytre.pipe.api;

public interface PipeLogic<C> {

    InsertionChecker<C> getInsertionChecker();

    PipeConnectable<C> getPipeConnectable();

    StorageExtractor<C> getStorageExtractor();

    StorageFinder<C> getStorgeFinder();

    StorageInserter<C> getStorageInserter();
}
