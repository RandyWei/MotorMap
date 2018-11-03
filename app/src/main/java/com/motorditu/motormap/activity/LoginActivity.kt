package com.motorditu.motormap.activity

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.TargetApi
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView

import com.motorditu.motormap.R

import kotlinx.android.synthetic.main.activity_login.*

/**
 * A login screen that offers login via email/password.
 */
class LoginActivity : BaseActivity() {
    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    private var mAuthTask: UserLoginTask? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        password.setOnEditorActionListener(TextView.OnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_NULL) {
                attemptLogin()
                return@OnEditorActionListener true
            }
            false
        })

        phone_sign_in_button.setOnClickListener { attemptLogin() }
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private fun attemptLogin() {
        if (mAuthTask != null) {
            return
        }

        // Reset errors.
        phone.error = null
        password.error = null

        // Store values at the time of the login attempt.
        val phoneStr = phone.text.toString()
        val passwordStr = password.text.toString()

        var cancel = false
        var focusView: View? = null


        // Check for a valid email address.
        if (TextUtils.isEmpty(phoneStr)) {
            phone.error = getString(R.string.error_field_required)
            focusView = phone
            cancel = true
        } else if (!isPhoneValid(phoneStr)) {
            phone.error = getString(R.string.error_invalid_phone)
            focusView = phone
            cancel = true
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView?.requestFocus()
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true)
            mAuthTask = UserLoginTask(phoneStr, passwordStr)
            mAuthTask!!.execute(null as Void?)
        }
    }

    private fun isPhoneValid(phone: String): Boolean {
        /*
         * 基础运营商：中国移动（M），中国联通（U），中国电信（T），北京船舶通信导航有限公司（B）
         * 虚拟运营商（X）
         * 通信业务：物联网（W），卫星移动通信网（S）
         * ==========================================================================================
         *       0             1         2     3     4            5     6     7            8     9
         *    ---------------------------------------------------------------------------------------
         *  3 | U130          U131      U132  T133  M134         M135  M136  M137         M138  M139
         *    |                                     T134(9)S
         *    |
         *  4 |               T141(0)W              M144(0)W     U145  U146  M147         M148  T149W
         *    |
         *  5 | M150          M151      M152  T153               U155  U156  M157(0-7,9)  M158  M159
         *    |
         *  6 |                                                        U166
         *    |
         *  7 | T170(0-2)X    U171X     M172  T173  T174(00-05)  U175  U176  T177         M178
         *    | M170(3,5,6)X                        B174(9)
         *    | U170(4,7-9)X
         *    |
         *  8 | T180          T181      M182  M183  M184         U185  U186  M187         M188  T189
         *    |
         *  9 |                                                                           M198  T199
         * ==========================================================================================
         */
        return phone.matches(Regex("^1(([38][0-9])|(4[14-9])|(5[0-35-9])|66|(7[0-8])|(9[89]))\\d{8}$"))
    }


    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private fun showProgress(show: Boolean) {

        val shortAnimTime = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()

        login_form.visibility = if (show) View.GONE else View.VISIBLE
        login_form.animate()
                .setDuration(shortAnimTime)
                .alpha((if (show) 0 else 1).toFloat())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        login_form.visibility = if (show) View.GONE else View.VISIBLE
                    }
                })

        login_progress.visibility = if (show) View.VISIBLE else View.GONE
        login_progress.animate()
                .setDuration(shortAnimTime)
                .alpha((if (show) 1 else 0).toFloat())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        login_progress.visibility = if (show) View.VISIBLE else View.GONE
                    }
                })
    }


    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    inner class UserLoginTask internal constructor(private val mEmail: String, private val mPassword: String) : AsyncTask<Void, Void, Boolean>() {

        override fun doInBackground(vararg params: Void): Boolean? {
            // TODO: attempt authentication against a network service.

            try {
                // Simulate network access.
                Thread.sleep(2000)
            } catch (e: InterruptedException) {
                return false
            }

            return DUMMY_CREDENTIALS
                    .map { it.split(":") }
                    .firstOrNull { it[0] == mEmail }
                    ?.let {
                        // Account exists, return true if the password matches.
                        it[1] == mPassword
                    }
                    ?: true
        }

        override fun onPostExecute(success: Boolean?) {
            mAuthTask = null
            showProgress(false)

            if (success!!) {
                finish()
            } else {
                password.error = getString(R.string.error_incorrect_password)
                password.requestFocus()
            }
        }

        override fun onCancelled() {
            mAuthTask = null
            showProgress(false)
        }
    }

    companion object {

        /**
         * Id to identity READ_CONTACTS permission request.
         */
        private val REQUEST_READ_CONTACTS = 0

        /**
         * A dummy authentication store containing known user names and passwords.
         * TODO: remove after connecting to a real authentication system.
         */
        private val DUMMY_CREDENTIALS = arrayOf("foo@example.com:hello", "bar@example.com:world")
    }
}
