# Deprecated Items to Remove

Total: ~103 deprecated items across 34 files.

## `io.nats.client.Options`
| Line | Item | Type |
|------|------|------|
| 113 | `MINIMUM_SOCKET_WRITE_TIMEOUT_GT_CONNECTION_TIMEOUT` | constant |
| 124 | `MINIMUM_SOCKET_READ_TIMEOUT_GT_CONNECTION_TIMEOUT` | constant |
| 462 | `PROP_NO_RESOLVE_HOSTNAMES` | constant |
| 469 | `PROP_FAST_FALLBACK` | constant |
| 897 | `setOldRequestStyle(boolean)` | method |
| 1215 | `Builder.noResolveHostnames()` | method |
| 1226 | `Builder.enableFastFallback()` | method |
| 1804 | `Builder.token(String)` | method |
| 2793 | `isNoResolveHostnames()` | method |
| 2803 | `isEnableFastFallback()` | method |
| 3070 | `getUsername()` | method |
| 3087 | `getPassword()` | method |
| 3104 | `getToken()` | method |

## `io.nats.client.Message`
| Line | Item | Type |
|------|------|------|
| 74 | `isUtf8mode()` | method |

## `io.nats.client.ConnectionListener`
| Line | Item | Type |
|------|------|------|
| 88 | `connectionEvent(Connection, Events)` | method |

## `io.nats.client.StatisticsCollector`
| Line | Item | Type |
|------|------|------|
| 95 | `incrementInMsgs()` | method |
| 102 | `incrementOutMsgs()` | method |
| 110 | `incrementInBytes(long)` | method |
| 118 | `incrementOutBytes(long)` | method |

## `io.nats.client.ServerPool`
| Line | Item | Type |
|------|------|------|
| 49 | `resolveHostToIps(String)` | method |

## `io.nats.client.KeyValueManagement`
| Line | Item | Type |
|------|------|------|
| 54 | `getBucketInfo(String)` | method |

## `io.nats.client.JetStreamOptions`
| Line | Item | Type |
|------|------|------|
| 20 | `DEFAULT_TIMEOUT` | constant |

## `io.nats.client.JetStreamApiException`
| Line | Item | Type |
|------|------|------|
| 22 | `JetStreamApiException(ApiResponse<?>)` | constructor |

## `io.nats.client.JetStreamStatusException`
| Line | Item | Type |
|------|------|------|
| 56 | `getDescription()` | method |

## `io.nats.client.PublishOptions`
| Line | Item | Type |
|------|------|------|
| 22 | `UNSET_STREAM` | constant |
| 82 | `getStream()` | method |
| 207 | `Builder.stream(String)` | method |

## `io.nats.client.PushSubscribeOptions`
| Line | Item | Type |
|------|------|------|
| 46 | `bind(String)` | method |

## `io.nats.client.impl.DataPort`
| Line | Item | Type |
|------|------|------|
| 15 | `connect(String, NatsConnection, long)` | method |

## `io.nats.client.impl.SocketDataPort`
| Line | Item | Type |
|------|------|------|
| 43 | `connect(String, NatsConnection, long)` | method |

## `io.nats.client.impl.NatsServerPool`
| Line | Item | Type |
|------|------|------|
| 200 | `resolveHostToIps(String)` | method |

## `io.nats.client.impl.NatsMessage`
| Line | Item | Type |
|------|------|------|
| 60 | `NatsMessage(String, String, byte[], boolean)` | constructor |
| 65 | `NatsMessage(String, String, Headers, byte[], boolean)` | constructor |
| 523 | `Builder.utf8mode(boolean)` | method |

## `io.nats.client.impl.Headers`
| Line | Item | Type |
|------|------|------|
| 495 | `appendSerialized(ByteArrayBuilder)` | method |

## `io.nats.client.api.ApiStats`
| Line | Item | Type |
|------|------|------|
| 62 | `getTotal()` | method (returns int, use long version) |
| 72 | `getErrors()` | method (returns int, use long version) |

## `io.nats.client.api.AccountTier`
| Line | Item | Type |
|------|------|------|
| 110 | `getMemory()` | method (returns int, use long version) |
| 120 | `getStorage()` | method (returns int, use long version) |

## `io.nats.client.api.MessageInfo`
| Line | Item | Type |
|------|------|------|
| 35 | `MessageInfo(Message)` | constructor |

## `io.nats.client.api.MessageGetRequest`
| Line | Item | Type |
|------|------|------|
| 105 | `seqBytes(long)` | method |
| 115 | `lastBySubjectBytes(String)` | method |
| 125 | `MessageGetRequest(long)` | constructor |
| 135 | `MessageGetRequest(String)` | constructor |

## `io.nats.client.api.KeyValueConfiguration`
| Line | Item | Type |
|------|------|------|
| 37 | `getMaxValueSize()` | method |
| 229 | `Builder.maxValueSize(long)` | method |

## `io.nats.client.api.KeyValueStatus`
| Line | Item | Type |
|------|------|------|
| 100 | `getMaxValueSize()` | method |

## `io.nats.client.api.StreamConfiguration`
| Line | Item | Type |
|------|------|------|
| 320 | `getMaxMsgSize()` | method |
| 511 | `isAllowMessageTtl()` | method |
| 843 | `Builder.maxMsgSize(long)` | method |

## `io.nats.client.api.PurgeResponse`
| Line | Item | Type |
|------|------|------|
| 44 | `getPurgedCount()` | method (returns int, use long version) |

## `io.nats.client.api.ConsumerConfiguration`
| Line | Item | Type |
|------|------|------|
| 1629 | `DURATION_MIN` | constant |

## `io.nats.client.support.JsonUtils` (old regex-based JSON parsing)
| Line | Item | Type |
|------|------|------|
| 431 | `normalize(String)` | method |
| 440 | `objectString(String, Object)` | method |
| 542 | `FieldType` | enum |
| 556 | `string_pattern(String)` | method |
| 561 | `number_pattern(String)` | method |
| 566 | `integer_pattern(String)` | method |
| 571 | `boolean_pattern(String)` | method |
| 576 | `string_array_pattern(String)` | method |
| 581 | `number_array_pattern(String)` | method |
| 592 | `buildPattern(String, FieldType)` | method |
| 597 | `buildPattern(String, String)` | method |
| 608 | `getJsonObject(String, String)` | method |
| 613 | `getJsonObject(String, String, String)` | method |
| 619 | `removeObject(String, String)` | method |
| 637 | `getObjectList(String, String)` | method |
| 704 | `getMapOfObjects(String)` | method |
| 729 | `getMapOfLists(String)` | method |
| 754 | `getMapOfLongs(String)` | method |
| 781 | `getStringList(String, String)` | method |
| 811 | `getLongList(String, String)` | method |
| 833 | `getDurationList(String, String)` | method |
| 843 | `simpleMessageBody(String, Number)` | method |
| 848 | `simpleMessageBody(String, String)` | method |
| 853 | `readString(String, Pattern)` | method |
| 858 | `readString(String, Pattern, String)` | method |
| 864 | `readStringMayHaveQuotes(String, String, String)` | method |
| 895 | `readBytes(String, Pattern)` | method |
| 901 | `readBase64(String, Pattern)` | method |
| 908 | `readBoolean(String, Pattern)` | method |
| 914 | `readBoolean(String, Pattern, Boolean)` | method |
| 923 | `readInteger(String, Pattern)` | method |
| 929 | `readInt(String, Pattern, int)` | method |
| 935 | `readInt(String, Pattern, IntConsumer)` | method |
| 943 | `readLong(String, Pattern)` | method |
| 949 | `readLong(String, Pattern, long)` | method |
| 955 | `readLong(String, Pattern, LongConsumer)` | method |
| 966 | `readDate(String, Pattern)` | method |
| 972 | `readNanos(String, Pattern)` | method |
| 978 | `readNanos(String, Pattern, Duration)` | method |
| 984 | `readNanos(String, Pattern, Consumer<Duration>)` | method |

## `io.nats.client.support.JsonValueUtils`
| Line | Item | Type |
|------|------|------|
| 333 | `MapBuilder.getJsonValue()` | method |
| 367 | `ArrayBuilder.getJsonValue()` | method |

## `io.nats.client.support.Validator`
| Line | Item | Type |
|------|------|------|
| 187 | `required(String, String, String)` | method |

## `io.nats.client.support.Encoding`
| Line | Item | Type |
|------|------|------|
| 370 | `base64Encode(byte[])` | method |
| 381 | `toBase64Url(byte[])` | method |
| 392 | `toBase64Url(String)` | method |
| 403 | `fromBase64Url(String)` | method |

## `io.nats.client.support.ApiConstants`
| Line | Item | Type |
|------|------|------|
| 228 | `TLS` | constant |

## `io.nats.client.support.NatsConstants`
| Line | Item | Type |
|------|------|------|
| 86 | `WSS_PROTOCOLS` | constant |

## `io.nats.client.support.NatsJetStreamClientError`
| Line | Item | Type |
|------|------|------|
| 61 | `JsSubFcHbHbNotValidQueue` | constant (spelling fix) |
| 64 | `JsConsumerCantUseNameBefore290` | constant (renamed) |
| 67 | `JsSoOrderedRequiresMaxDeliver` | constant (renamed) |

## `io.nats.client.support.NatsJetStreamConstants`
| Line | Item | Type |
|------|------|------|
| 9 | `MAX_PULL_SIZE` | constant |

## `io.nats.service.ServiceBuilder`
| Line | Item | Type |
|------|------|------|
| 149 | `schemaDispatcher(Dispatcher)` | method |
