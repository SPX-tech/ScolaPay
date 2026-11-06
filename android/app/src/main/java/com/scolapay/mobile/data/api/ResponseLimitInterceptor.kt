package com.scolapay.mobile.data.api

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

class ResponseLimitInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body ?: return response
        val limit = 4L * 1024 * 1024
        if (body.contentLength() > limit) {
            body.close()
            throw IOException("Réponse API trop volumineuse")
        }
        val source = object : ForwardingSource(body.source()) {
            private var total = 0L
            override fun read(sink: Buffer, byteCount: Long): Long {
                val count = super.read(sink, minOf(byteCount, limit - total + 1))
                if (count > 0) total += count
                if (total > limit) {
                    close()
                    throw IOException("Réponse API trop volumineuse")
                }
                return count
            }
        }.buffer()
        return response.newBuilder().body(object : ResponseBody() {
            override fun contentType() = body.contentType()
            override fun contentLength() = body.contentLength()
            override fun source(): BufferedSource = source
        }).build()
    }
}
