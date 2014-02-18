/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.plugins.mercurial;

import hudson.Extension;
import org.jenkinsci.lib.configprovider.AbstractConfigProviderImpl;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.lib.configprovider.model.ContentType;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Config file in INI format, akin to {@code ~/.hgrc} (Unix) or {@code Mercurial.ini} (Windows).
 */
public final class MercurialIniConfig extends Config {

    @DataBoundConstructor public MercurialIniConfig(String id, String name, String comment, String content) {
        super(id, name, comment, content);
    }

    @Extension public static final class MercurialIniConfigProvider extends AbstractConfigProviderImpl {

        @Override public String getDisplayName() {
            return "Mercurial initialization file";
        }

        @Override public ContentType getContentType() {
            return null; // http://stackoverflow.com/questions/16088354/whats-the-content-type-of-a-ini-file
        }

        @Override public Config newConfig() {
            return new MercurialIniConfig(this.getProviderId() + "." + System.currentTimeMillis(), null, null, "[defaults]\n");
        }

    }

}
