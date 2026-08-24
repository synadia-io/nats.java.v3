# API Abbreviation Audit

Abbreviated terms found in public getter/setter method names across jsapi and api packages.

## msg / msgs (message / messages)

| Method | Class | Full form would be |
|--------|-------|--------------------|
| getMaxMsgSize | StreamCreator, StreamConfiguration | getMaxMessageSize |
| maxMsgSize | StreamCreator | maxMessageSize |
| getMaxMsgs | StreamCreator, StreamConfiguration | getMaxMessages |
| getMaxMsgsPerSubject | StreamCreator, StreamConfiguration | getMaxMessagesPerSubject |
| getMsgCount | StreamState | getMessageCount |
| getAllowMsgSchedules | StreamCreator, StreamConfiguration | getAllowMessageSchedules |

## num (number)

| Method | Class | Full form would be |
|--------|-------|--------------------|
| getNumAckPending | ConsumerInfo | getNumberOfAckPending |
| getNumPending | ConsumerInfo | getNumberOfPending |
| getNumReplicas | ConsumerCreator, ConsumerConfiguration | getNumberOfReplicas |
| getNumWaiting | ConsumerInfo | getNumberOfWaiting |
| numReplicas | ConsumerCreator | numberOfReplicas |

## seq (sequence)

| Method | Class | Full form would be |
|--------|-------|--------------------|
| getStartSeq | StreamSource, StreamSourceInfo | getStartSequence |
| startSeq | StreamSourceCreator | startSequence |
| getLastSeq | MessageInfo | getLastSequence |
| getSeq | MessageInfo | getSequence |

## ack (acknowledge/acknowledgment)

Already a well-known domain term. Not really an abbreviation in context — "ack" IS the term.

- ackPolicy, ackWait, getAckFloor, getMaxAckPending, getNoAck, getNumAckPending, maxAckPending, noAck

## ttl (time to live)

Standard acronym, not really an abbreviation. Used consistently.

- getTtl, ttl, getAllowMessageTtl, allowMessageTtl, getSubjectDeleteMarkerTtl, subjectDeleteMarkerTtl, getLimitMarkerTtl

## max (maximum)

Standard prefix, not an abbreviation. Used consistently across all max* methods.

## freq (frequency)

| Method | Class | Full form would be |
|--------|-------|--------------------|
| getSampleFrequency | ConsumerCreator, ConsumerConfiguration | (already full) |
| sampleFrequency | ConsumerCreator | (already full) |

Note: "frequency" is already spelled out — "freq" only appears in the JSON key `sample_freq`, not in the method name.

## dup (duplicate)

| Method | Class | Full form would be |
|--------|-------|--------------------|
| getDuplicateWindow | StreamCreator, StreamConfiguration | (already full) |
| duplicateWindow | StreamCreator | (already full) |
| isDuplicate | PublishAck | (already full) |

Note: "duplicate" is already spelled out in method names.

## Summary

The real abbreviations that differ from their full form are:

| Abbreviation | Full | Methods affected |
|---|---|---|
| msg/msgs | message/messages | 6 methods (getMaxMsgSize, maxMsgSize, getMaxMsgs, getMaxMsgsPerSubject, getMsgCount, getAllowMsgSchedules) |
| num | numberOfXxx | 5 methods (getNumAckPending, getNumPending, getNumReplicas, getNumWaiting, numReplicas) |
| seq | sequence | 4 methods (getStartSeq, startSeq, getLastSeq, getSeq) |

All other "abbreviations" (ack, ttl, max, freq, dup) are either standard terms, acronyms, or already spelled out in the method names.

### Note on seq vs sequence inconsistency

`StreamSourceCreator` has `startSeq(long)` while `ConsumerCreator` has `startSequence(long)`.
`StreamSource` has `getStartSeq()` while `ConsumerCreator` has `getStartSequence()`.
This is inconsistent — they should match.
