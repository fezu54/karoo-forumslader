package io.hammerhead.karooext.models

/**
 * In Karoo SDK, hiding custom stream state overlay is achieved by emitting ShowCustomStreamState with null message.
 */
val HideCustomStreamState: ShowCustomStreamState = ShowCustomStreamState(message = null, color = null)
