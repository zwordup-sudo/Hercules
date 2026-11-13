package com.rafa.hercules.data

/**
 * Plurales para conteos visibles (v1.16): evita "1 ejercicios" o "1 serie s".
 * Se centraliza aquí para no repetir la regla en cada pantalla.
 */
fun countLabel(count: Int, singular: String, plural: String): String =
    "$count ${if (count == 1) singular else plural}"