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

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckoutModelsTest {

    private val moshi = Moshi.Builder().build()

    @Test
    fun testSampleProductDeserialization() {
        val json = """{"id":"price_test_123","name":"テスト商品","amount":100}"""
        val adapter = moshi.adapter(SampleProduct::class.java)
        val product = adapter.fromJson(json)

        assertNotNull(product)
        assertEquals("price_test_123", product!!.id)
        assertEquals("テスト商品", product.name)
        assertEquals(100, product.amount)
    }

    @Test
    fun testProductsResponseDeserialization() {
        val json = """{"products":[{"id":"price_test_123","name":"テスト商品","amount":100}]}"""
        val adapter = moshi.adapter(ProductsResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertEquals(1, response!!.products.size)
        assertEquals("price_test_123", response.products[0].id)
        assertEquals("テスト商品", response.products[0].name)
        assertEquals(100, response.products[0].amount)
    }

    @Test
    fun testProductsResponseEmptyList() {
        val json = """{"products":[]}"""
        val adapter = moshi.adapter(ProductsResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertEquals(0, response!!.products.size)
    }

    @Test
    fun testCheckoutSessionRequestSerialization() {
        val request = CheckoutSessionRequest(
            price_id = "price_test_123",
            quantity = 1,
            success_url = "payjpcheckoutexample://checkout/success",
            cancel_url = "payjpcheckoutexample://checkout/cancel"
        )
        val adapter = moshi.adapter(CheckoutSessionRequest::class.java)
        val json = adapter.toJson(request)

        assertNotNull(json)
        assertTrue(json.contains("\"price_id\":\"price_test_123\""))
        assertTrue(json.contains("\"quantity\":1"))
        assertTrue(json.contains("\"success_url\":\"payjpcheckoutexample://checkout/success\""))
        assertTrue(json.contains("\"cancel_url\":\"payjpcheckoutexample://checkout/cancel\""))
    }

    @Test
    fun testCheckoutSessionResponseDeserialization() {
        val json = """{"id":"cs_test_123","url":"https://checkout.pay.jp/test","status":"open"}"""
        val adapter = moshi.adapter(CheckoutSessionResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertEquals("cs_test_123", response!!.id)
        assertEquals("https://checkout.pay.jp/test", response.url)
        assertEquals("open", response.status)
    }
}
