package com.dculus.stayfocused.core.ui.components

private const val LINK_CODE_LENGTH = 6
private const val LINK_CODE_GROUP = 3

/** `482913` -> `482 913`, the way both phones show the pairing code. Anything but 6 characters is returned as is. */
fun formatLinkCode(code: String): String =
    if (code.length == LINK_CODE_LENGTH) "${code.take(LINK_CODE_GROUP)} ${code.drop(LINK_CODE_GROUP)}" else code
