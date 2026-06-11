/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.impl

import okhttp3.HttpUrl
import okhttp3.Request
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * AWS Signature Version 4 request signer.
 *
 * Mirrors the iOS `AWSV4Signer`: signs a request for an S3 (or MinIO) PUT by computing the
 * canonical request, string-to-sign, signing key, and Authorization header. The body is hashed
 * separately so the caller controls the exact bytes that are uploaded.
 */
internal class AwsV4Signer(
    private val accessKeyId: String,
    private val secretAccessKey: String,
    private val sessionToken: String?,
    private val region: String,
    private val service: String,
) {
    /**
     * Returns the headers (including Authorization) that must be added to a PUT request for [url]
     * carrying [body], signed for the supplied [date].
     */
    fun signedHeaders(
        method: String,
        url: HttpUrl,
        contentType: String,
        body: ByteArray,
        date: Date = Date(),
    ): Map<String, String> {
        val dateString = DATE_FORMAT.get()!!.format(date)
        val datetimeString = DATETIME_FORMAT.get()!!.format(date)

        val bodyHash = sha256Hex(body)
        val host = url.host

        // Headers that participate in the signature.
        val headers = sortedMapOf<String, String>()
        headers["content-type"] = contentType
        headers["host"] = host
        headers["x-amz-content-sha256"] = bodyHash
        headers["x-amz-date"] = datetimeString
        sessionToken?.let { headers["x-amz-security-token"] = it }

        // AWS Sig V4: each path segment must encode all chars except unreserved (A-Z a-z 0-9 - _ . ~).
        val rawPath = url.encodedPath.ifEmpty { "/" }
        val canonicalUri = rawPath
            .split("/")
            .joinToString("/") { uriEncode(decodeOnce(it), encodeSlash = true) }
        val canonicalQuery = canonicalQueryString(url)

        val signedHeaderNames = headers.keys.toList()
        val canonicalHeaders = signedHeaderNames.joinToString("") { name ->
            "$name:${headers[name]!!.trim()}\n"
        }
        val signedHeadersString = signedHeaderNames.joinToString(";")

        val canonicalRequest = listOf(
            method,
            canonicalUri,
            canonicalQuery,
            canonicalHeaders,
            signedHeadersString,
            bodyHash,
        ).joinToString("\n")

        val credentialScope = "$dateString/$region/$service/aws4_request"
        val canonicalRequestHash = sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8))
        val stringToSign = "AWS4-HMAC-SHA256\n$datetimeString\n$credentialScope\n$canonicalRequestHash"

        val signingKey = deriveSigningKey(dateString)
        val signature = hmacSha256(signingKey, stringToSign.toByteArray(Charsets.UTF_8)).toHex()

        val authHeader =
            "AWS4-HMAC-SHA256 Credential=$accessKeyId/$credentialScope, " +
                "SignedHeaders=$signedHeadersString, Signature=$signature"

        return buildMap {
            put("Content-Type", contentType)
            put("x-amz-date", datetimeString)
            put("x-amz-content-sha256", bodyHash)
            sessionToken?.let { put("x-amz-security-token", it) }
            put("Authorization", authHeader)
        }
    }

    /** Applies the signed headers to a request builder. host is set automatically by OkHttp from the URL. */
    fun sign(builder: Request.Builder, method: String, url: HttpUrl, contentType: String, body: ByteArray) {
        signedHeaders(method, url, contentType, body).forEach { (name, value) ->
            builder.header(name, value)
        }
    }

    private fun deriveSigningKey(dateString: String): ByteArray {
        val dateKey = hmacSha256("AWS4$secretAccessKey".toByteArray(Charsets.UTF_8), dateString.toByteArray(Charsets.UTF_8))
        val regionKey = hmacSha256(dateKey, region.toByteArray(Charsets.UTF_8))
        val serviceKey = hmacSha256(regionKey, service.toByteArray(Charsets.UTF_8))
        return hmacSha256(serviceKey, "aws4_request".toByteArray(Charsets.UTF_8))
    }

    private fun canonicalQueryString(url: HttpUrl): String {
        if (url.querySize == 0) return ""
        val items = (0 until url.querySize).map { index ->
            url.queryParameterName(index) to (url.queryParameterValue(index) ?: "")
        }
        return items
            .sortedBy { it.first }
            .joinToString("&") { (name, value) ->
                "${uriEncode(name)}=${uriEncode(value)}"
            }
    }

    private fun decodeOnce(value: String): String =
        runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    /** RFC 3986 percent-encoding (unreserved chars: A-Z a-z 0-9 - _ . ~). */
    private fun uriEncode(value: String, encodeSlash: Boolean = true): String {
        val builder = StringBuilder()
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val ch = byte.toInt() and 0xFF
            val c = ch.toChar()
            when {
                c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_' || c == '.' || c == '~' ->
                    builder.append(c)
                c == '/' && !encodeSlash -> builder.append(c)
                else -> builder.append('%').append(String.format(Locale.US, "%02X", ch))
            }
        }
        return builder.toString()
    }

    private fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).toHex()

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun ByteArray.toHex(): String = joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 0xFF) }

    private companion object {
        val DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        }
        val DATETIME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        }
    }
}
