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

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CheckoutRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: CheckoutRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = CheckoutRepository()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testFetchProductsSuccess() = runBlocking {
        val json = """{"products":[{"id":"price_test_123","name":"テスト商品","amount":100}]}"""
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json)
        )

        val result = repository.fetchProducts(server.url("/").toString())

        assertTrue(result.isSuccess)
        val products = result.getOrNull()!!
        assertEquals(1, products.size)
        assertEquals("price_test_123", products[0].id)
        assertEquals("テスト商品", products[0].name)
        assertEquals(100, products[0].amount)
    }

    @Test
    fun testFetchProductsEmpty() = runBlocking {
        val json = """{"products":[]}"""
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json)
        )

        val result = repository.fetchProducts(server.url("/").toString())

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()!!.size)
    }

    @Test
    fun testFetchProductsNetworkError() = runBlocking {
        server.shutdown()

        val result = repository.fetchProducts("http://localhost:1/")

        assertTrue(result.isFailure)
    }

    @Test
    fun testCreateSessionSuccess() = runBlocking {
        val json = """{"id":"cs_test_123","url":"https://checkout.pay.jp/test","status":"open"}"""
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json)
        )

        val result = repository.createSession(
            baseUrl = server.url("/").toString(),
            productId = "price_test_123",
            successUrl = "payjpcheckoutexample://checkout/success",
            cancelUrl = "payjpcheckoutexample://checkout/cancel"
        )

        assertTrue(result.isSuccess)
        val session = result.getOrNull()!!
        assertEquals("cs_test_123", session.id)
        assertEquals("https://checkout.pay.jp/test", session.url)
        assertEquals("open", session.status)
    }

    @Test
    fun testCreateSessionRequestBody() = runBlocking {
        val json = """{"id":"cs_test_123","url":"https://checkout.pay.jp/test","status":"open"}"""
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json)
        )

        repository.createSession(
            baseUrl = server.url("/").toString(),
            productId = "price_test_456",
            successUrl = "payjpcheckoutexample://checkout/success",
            cancelUrl = "payjpcheckoutexample://checkout/cancel"
        )

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"price_id\":\"price_test_456\""))
        assertTrue(body.contains("\"quantity\":1"))
        assertTrue(body.contains("\"success_url\":\"payjpcheckoutexample://checkout/success\""))
        assertTrue(body.contains("\"cancel_url\":\"payjpcheckoutexample://checkout/cancel\""))
    }
}
