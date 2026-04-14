package io.synadia.client.os;

import io.synadia.client.jsapi.Watcher;
import org.jspecify.annotations.NullMarked;

/**
 * Use the ObjectStoreWatcher interface to watch for updates
 */
@NullMarked
public interface ObjectStoreWatcher extends Watcher<ObjectInfo> {}
