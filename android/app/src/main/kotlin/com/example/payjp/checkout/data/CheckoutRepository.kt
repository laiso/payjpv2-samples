/*
 *
 * Copyright (c) 2021 PAY, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.example.payjp.checkout.data

import com.example.payjp.checkout.BuildConfig
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class CheckoutRepository {

    private var cachedService: Pair<String, CheckoutBackendService>? = null

    suspend fun fetchProducts(baseUrl: String): Result<List<SampleProduct>> {
        return runCatching {
            createService(baseUrl).getProducts().products
        }
    }

    suspend fun createSession(
        baseUrl: String,
        productId: String,
        successUrl: String,
        cancelUrl: String
    ): Result<CheckoutSessionResponse> {
        val body = CheckoutSessionRequest(
            price_id = productId,
            quantity = 1,
            success_url = successUrl,
            cancel_url = cancelUrl
        )
        return runCatching {
            createService(baseUrl).createCheckoutSession(body)
        }
    }

    private fun createService(baseUrl: String): CheckoutBackendService {
        val fixedBaseUrl = ensureTrailingSlash(baseUrl)
        cachedService?.let { (url, service) ->
            if (url == fixedBaseUrl) return service
        }
        val client = OkHttpClient.Builder().apply {
            if (BuildConfig.DEBUG) {
                val logging = HttpLoggingInterceptor()
                logging.setLevel(HttpLoggingInterceptor.Level.BASIC)
                addNetworkInterceptor(logging)
            }
        }.build()

        val retrofit = Retrofit.Builder()
            .baseUrl(fixedBaseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()

        return retrofit.create(CheckoutBackendService::class.java).also {
            cachedService = fixedBaseUrl to it
        }
    }

    private fun ensureTrailingSlash(baseUrl: String): String {
        return if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    }
}
