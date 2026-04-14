package io.synadia.client.kv;

import io.synadia.client.jsapi.Watcher;
import org.jspecify.annotations.NullMarked;

/**
 * Use the KeyValueWatcher interface to watch for updates
 */
@NullMarked
public interface KeyValueWatcher extends Watcher<KeyValueEntry> {}
