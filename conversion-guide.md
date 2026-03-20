# NATS Java Client v3 Conversion Guide

## Nullable Lists and Maps

In v3, list and map getters return `null` when the field was not present in the server response, rather than returning an empty collection. Code that previously assumed a non-null return must now check for `null`.

### Lists that are now nullable

| Class | Getter | Return Type | Notes |
|-------|--------|-------------|-------|
| ClusterInfo | `getReplicas()` | `List<Replica>` | null when no replicas |
| ConsumerConfiguration | `getFilterSubjects()` | `List<String>` | null when no filter subjects set |
| ConsumerConfiguration | `getPriorityGroups()` | `List<String>` | null when no priority groups set |
| ConsumerConfiguration | `getBackoff()` | `List<Duration>` | null when no backoff configured |
| ConsumerInfo | `getPriorityGroupStates()` | `List<PriorityGroupState>` | null when no priority groups |
| KeyValueConfiguration | `getSources()` | `List<Source>` | null when no sources |
| LostStreamData | `getMessages()` | `List<Long>` | null when no lost messages |
| OrderedConsumerConfiguration | `getFilterSubjects()` | `List<String>` | null when no filter subjects set |
| Placement | `getTags()` | `List<String>` | null when no tags set |
| SourceBase | `getSubjectTransforms()` | `List<SubjectTransform>` | null when no transforms; applies to Mirror and Source |
| SourceInfoBase | `getSubjectTransforms()` | `List<SubjectTransform>` | null when no transforms; applies to MirrorInfo and SourceInfo |
| StreamConfiguration | `getSources()` | `List<Source>` | null when no sources |
| StreamInfo | `getSourceInfos()` | `List<SourceInfo>` | null when no sources |
| StreamInfo | `getAlternates()` | `List<StreamAlternate>` | null when no alternates |
| StreamState | `getDeleted()` | `List<Long>` | null when no deleted sequences |
| StreamState | `getSubjects()` | `List<Subject>` | null when no subject details |
| StreamState | `getSubjectMap()` | `Map<String, Long>` | null when no subject details |

### Maps that are now nullable

| Class | Getter | Return Type | Notes |
|-------|--------|-------------|-------|
| ConsumerConfiguration | `getMetadata()` | `Map<String, String>` | null when no metadata set |
| StreamConfiguration | `getMetadata()` | `Map<String, String>` | null when no metadata set |
| FeatureConfiguration | `getMetadata()` | `Map<String, String>` | null when no metadata; applies to KeyValueConfiguration, ObjectStoreConfiguration |
| KeyValueStatus | `getMetadata()` | `Map<String, String>` | null when no metadata |
| ObjectStoreStatus | `getMetadata()` | `Map<String, String>` | null when no metadata |

### Lists that remain non-null (empty when absent)

| Class | Getter | Return Type | Notes |
|-------|--------|-------------|-------|
| StreamConfiguration | `getSubjects()` | `List<String>` | empty list when no subjects configured |
| ServerInfo | `getConnectURLs()` | `List<String>` | empty list when no connect URLs |

### Migration example

**Before (v2):**
```java
StreamInfo si = jsm.getStreamInfo("mystream");
for (SourceInfo source : si.getSourceInfos()) {  // safe, never null
    System.out.println(source.getName());
}
```

**After (v3):**
```java
StreamInfo si = jsm.getStreamInfo("mystream");
List<SourceInfo> sources = si.getSourceInfos();
if (sources != null) {
    for (SourceInfo source : sources) {
        System.out.println(source.getName());
    }
}
```
