/*
 * MIT License
 *
 * Copyright (c) 2017 Anders Mikkelsen
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

package com.nannoq.tools.auth.services.providers.utils

import com.nannoq.tools.auth.models.UserProfile
import com.nannoq.tools.repository.models.ModelUtils
import facebook4j.User

/**
 * This class defines a facebook user as created from a facebook token.
 *
 * @author Anders Mikkelsen
 * @version 13/11/17
 */
class FaceBookUser : UserProfile {
    constructor(user: User) {
        this.email = user.email
        this.name = user.name
        this.givenName = user.firstName
        this.familyName = user.lastName
        this.pictureUrl = if (user.picture != null) user.picture.url.toString() else "N/A"
        this.isEmailVerified = user.isVerified!!

        if (email.equals("", ignoreCase = true)) {
            generateFakeEmail(user)
        }
    }

    private fun generateFakeEmail(user: User) {
        this.email = ModelUtils.returnNewEtag((
                when {
                    user.id != null -> user.id.hashCode()
                else -> name.hashCode()
        }).toLong()) + "@facebook.notfound.com"
    }
}
