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
package com.example.payjp.checkout

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.payjp.checkout.data.CheckoutRepository
import com.example.payjp.checkout.data.SampleProduct
import com.example.payjp.checkout.databinding.ActivityCheckoutMainBinding
import com.example.payjp.checkout.databinding.ItemProductBinding
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CheckoutMainActivity : AppCompatActivity() {

    private val scope = MainScope()
    private val repository = CheckoutRepository()
    private lateinit var binding: ActivityCheckoutMainBinding
    private lateinit var adapter: ProductAdapter

    private var selectedProductId: String? = null
    private var isLoading: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.checkoutToolbar)
        title = getString(R.string.checkout_title)

        adapter = ProductAdapter { product ->
            selectedProductId = product.id
            adapter.selectedProductId = product.id
            updateCheckoutButton()
        }
        binding.productsList.layoutManager = LinearLayoutManager(this)
        binding.productsList.adapter = adapter

        val backendUrl = loadBackendUrl()
        binding.backendUrlInput.setText(backendUrl)
        logBackendUrlDiagnostics(backendUrl)

        binding.buttonFetchProducts.setOnClickListener {
            fetchProducts()
        }

        binding.buttonStartCheckout.setOnClickListener {
            startCheckout()
        }

        updateCheckoutButton()
        fetchProducts()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun fetchProducts() {
        if (isLoading) return
        val baseUrl = binding.backendUrlInput.text?.toString()?.trim().orEmpty()
        if (baseUrl.isEmpty()) {
            showError(getString(R.string.error_empty_url))
            return
        }
        if (!isValidUrl(baseUrl)) {
            showError(getString(R.string.error_invalid_url))
            return
        }

        saveBackendUrl(baseUrl)
        setLoading(true)
        selectedProductId = null
        adapter.submit(emptyList())
        updateCheckoutButton()

        scope.launch {
            val result = repository.fetchProducts(baseUrl)
            setLoading(false)
            result.fold(
                onSuccess = { products ->
                    if (products.isEmpty()) {
                        showError(getString(R.string.product_empty))
                    } else {
                        hideError()
                        adapter.submit(products)
                    }
                },
                onFailure = { throwable ->
                    showError("${getString(R.string.error_fetch_products)}: ${throwable.message}")
                }
            )
        }
    }

    private fun startCheckout() {
        val baseUrl = binding.backendUrlInput.text?.toString()?.trim().orEmpty()
        val productId = selectedProductId
        if (baseUrl.isEmpty()) {
            showError(getString(R.string.error_empty_url))
            return
        }
        if (!isValidUrl(baseUrl)) {
            showError(getString(R.string.error_invalid_url))
            return
        }
        if (productId == null) {
            Toast.makeText(this, getString(R.string.select_product_prompt), Toast.LENGTH_SHORT).show()
            return
        }

        saveBackendUrl(baseUrl)
        setLoading(true)

        val successUrl = "payjpcheckoutexample://checkout/success"
        val cancelUrl = "payjpcheckoutexample://checkout/cancel"

        scope.launch {
            val result = repository.createSession(baseUrl, productId, successUrl, cancelUrl)
            setLoading(false)
            result.fold(
                onSuccess = { session ->
                    hideError()
                    val intent = CustomTabsIntent.Builder().build()
                    intent.launchUrl(this@CheckoutMainActivity, Uri.parse(session.url))
                },
                onFailure = { throwable ->
                    showError("${getString(R.string.error_create_session)}: ${throwable.message}")
                }
            )
        }
    }

    private fun updateCheckoutButton() {
        binding.buttonStartCheckout.isEnabled = selectedProductId != null && !isLoading
        binding.buttonFetchProducts.isEnabled = !isLoading
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        binding.loadingIndicator.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
        updateCheckoutButton()
    }

    private fun showError(message: String) {
        binding.errorMessage.text = message
        binding.errorMessage.visibility = android.view.View.VISIBLE
    }

    private fun hideError() {
        binding.errorMessage.text = ""
        binding.errorMessage.visibility = android.view.View.GONE
    }

    private fun isValidUrl(value: String): Boolean {
        return value.startsWith("http://") || value.startsWith("https://")
    }

    private fun saveBackendUrl(url: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BACKEND_URL, url)
            .apply()
    }

    private fun loadBackendUrl(): String {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_BACKEND_URL, null) ?: return defaultBackendUrl()
        if (!isEmulator() && isLegacyEmulatorSavedUrl(saved)) {
            val migrated = defaultBackendUrl()
            prefs.edit().putString(KEY_BACKEND_URL, migrated).apply()
            Log.i(TAG, "migrated legacy emulator URL: saved=$saved -> $migrated")
            return migrated
        }
        return saved
    }

    /** Prior builds always persisted the emulator host; replace on real devices after LAN default shipped. */
    private fun isLegacyEmulatorSavedUrl(saved: String): Boolean {
        val normalized = saved.trim().trimEnd('/')
        return normalized == "http://10.0.2.2:3000"
    }

    /**
     * Emulator uses the special alias to the host loopback. Physical devices use the LAN IPv4
     * of the machine that built this APK (see app/build.gradle).
     */
    private fun defaultBackendUrl(): String {
        val host = if (isEmulator()) {
            "10.0.2.2"
        } else {
            BuildConfig.DEV_MACHINE_LAN_HOST
        }
        return "http://$host:3000"
    }

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("emulator64") ||
            Build.MODEL.contains("sdk_gphone") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            Build.PRODUCT == "google_sdk" ||
            Build.PRODUCT.startsWith("sdk_gphone") ||
            Build.PRODUCT.startsWith("emulator") ||
            Build.DEVICE.startsWith("emulator") ||
            Build.DEVICE.startsWith("sdk_gphone")
    }

    private fun logBackendUrlDiagnostics(loadedUrl: String) {
        Log.d(
            TAG,
            "backendUrl isEmulator=${isEmulator()} " +
                "buildDevLanHost=${BuildConfig.DEV_MACHINE_LAN_HOST} " +
                "defaultUrl=${defaultBackendUrl()} " +
                "loadedUrl=$loadedUrl"
        )
    }

    private class ProductAdapter(
        private val onClick: (SampleProduct) -> Unit
    ) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

        private val items = mutableListOf<SampleProduct>()
        var selectedProductId: String? = null

        fun submit(newItems: List<SampleProduct>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ItemProductBinding.inflate(inflater, parent, false)
            return ProductViewHolder(binding, onClick)
        }

        override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
            holder.bind(items[position], selectedProductId)
        }

        override fun getItemCount(): Int = items.size

        class ProductViewHolder(
            private val binding: ItemProductBinding,
            private val onClick: (SampleProduct) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(product: SampleProduct, selectedId: String?) {
                binding.productName.text = product.name
                binding.productAmount.text = "¥${product.amount}"
                binding.productRadio.isChecked = product.id == selectedId
                binding.root.setOnClickListener { onClick(product) }
                binding.productRadio.setOnClickListener { onClick(product) }
            }
        }
    }

    private companion object {
        private const val TAG = "CheckoutSample"
        private const val PREFS_NAME = "checkout_sample"
        private const val KEY_BACKEND_URL = "backend_url"
    }
}
