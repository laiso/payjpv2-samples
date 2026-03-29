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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.payjp.checkout.databinding.ActivityCheckoutResultBinding

class CheckoutResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCheckoutResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val result = resolveResult(intent?.data)
        if (result == ResultType.Success) {
            binding.resultIcon.setImageResource(R.drawable.ic_check_circle_24)
            binding.resultMessage.text = getString(R.string.result_success)
        } else {
            binding.resultIcon.setImageResource(R.drawable.ic_cancel_24)
            binding.resultMessage.text = getString(R.string.result_cancel)
        }

        binding.buttonBack.setOnClickListener {
            navigateBackToMain()
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    navigateBackToMain()
                }
            }
        )
    }

    private fun resolveResult(uri: Uri?): ResultType {
        return if (uri?.host == "checkout" && uri.path == "/success") {
            ResultType.Success
        } else {
            ResultType.Cancel
        }
    }

    private fun navigateBackToMain() {
        val intent = Intent(this, CheckoutMainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        startActivity(intent)
        finish()
    }

    private enum class ResultType {
        Success,
        Cancel
    }
}
