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
import org.jinstagram.entity.users.basicinfo.UserInfo

/**
 * This class defines a InstaGram user as created from an InstaGram token.
 *
 * @author Anders Mikkelsen
 * @version 13/11/17
 */
class InstaGramUser : UserProfile {
    constructor(user: UserInfo) {
        this.email = if (user.data != null) user.data.username else "N/A"
        this.name = if (user.data != null) user.data.fullName else "N/A"
        this.givenName = if (user.data != null) user.data.firstName else "N/A"
        this.familyName = if (user.data != null) user.data.lastName else "N/A"
        this.pictureUrl = if (user.data != null) user.data.profilePicture else "N/A"
        this.isEmailVerified = true
    }
}
