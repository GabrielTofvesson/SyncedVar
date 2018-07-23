package net.tofvesson.networking

/**
 * An annotation denoting that a field should be automatically serialized to bytes.
 * @param noCompress specifies whether or not the SyncedVar value should be losslessly compressed during serialization
 * @param nonNegative An advanced compression flag that may decrease serialization size at the cost of never having
 * negative values. NOTE: If the field is not a <i>short</i>, <i>int</i> or <i>long</i> or <b>noCompress</b> is set to
 * <b>true</b>, this parameter is ignored. This will cause errors if used in conjunction with a negative integer value.
 * @param floatEndianSwap Whether or not floating point values should have their endianness swapped before compression.
 * Swapping endianness may improve compression rates. This parameter is ignored if <b>noCompress</b> is set to <b>true</b>.
 */
@Target(allowedTargets = [(AnnotationTarget.FIELD)])
annotation class SyncedVar(val noCompress: Boolean = false, val nonNegative: Boolean = false, val floatEndianSwap: Boolean = true)