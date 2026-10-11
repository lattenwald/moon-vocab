package org.lattenwald.moonvocab.domain.card

enum class CardStatus { PENDING, FAILED, SENT, DISCARDED, DELETED_IN_ANKI }

enum class CardEvent { TRANSLATED, TRANSLATION_FAILED, SEND, UNDO_SEND, DISCARD, RESTORE, DELETE_FROM_ANKI, NOTE_MISSING, SEND_AS_NEW }

// Spec §5.8 card status table.
fun next(status: CardStatus?, event: CardEvent, previous: CardStatus? = null): CardStatus = when {
    status == null && event == CardEvent.TRANSLATED -> CardStatus.PENDING
    status == null && event == CardEvent.TRANSLATION_FAILED -> CardStatus.FAILED
    status == CardStatus.FAILED && event == CardEvent.TRANSLATED -> CardStatus.PENDING
    status == CardStatus.PENDING && event == CardEvent.SEND -> CardStatus.SENT
    status == CardStatus.SENT && event == CardEvent.UNDO_SEND -> CardStatus.PENDING
    (status == CardStatus.PENDING || status == CardStatus.FAILED) && event == CardEvent.DISCARD -> CardStatus.DISCARDED
    status == CardStatus.DISCARDED && event == CardEvent.RESTORE -> checkNotNull(previous) { "restore without a previous status" }
    status == CardStatus.SENT && event == CardEvent.DELETE_FROM_ANKI -> CardStatus.DISCARDED
    status == CardStatus.SENT && event == CardEvent.NOTE_MISSING -> CardStatus.DELETED_IN_ANKI
    status == CardStatus.DELETED_IN_ANKI && event == CardEvent.SEND_AS_NEW -> CardStatus.SENT
    (status == CardStatus.PENDING || status == CardStatus.SENT) && event == CardEvent.TRANSLATED -> status
    else -> throw IllegalStateException("no transition from $status on $event")
}
