/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.startup

import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.openapi.project.Project
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ClearResourceCacheAfterFirstBuildTest {
  @JvmField @Rule val projectRule = AndroidProjectRule.inMemory().initAndroid(false)

  private lateinit var project: Project
  private lateinit var clearResourceCacheAfterFirstBuild: ClearResourceCacheAfterFirstBuild
  private lateinit var onCacheClean: TestRunnable
  private lateinit var onSourceGenerationError: TestRunnable

  private class TestRunnable(private val toRun: () -> Unit) : Runnable {
    private var hasRun = false

    override fun run() {
      toRun()
      hasRun = true
    }

    fun assertHasRun() = assertThat(hasRun).isTrue()
    fun assertHasNotRun() = assertThat(hasRun).isFalse()
  }

  @Before
  fun setUp() {
    project = projectRule.project
    clearResourceCacheAfterFirstBuild = ClearResourceCacheAfterFirstBuild(project)

    onCacheClean = TestRunnable {
      assertWithMessage("onCacheClean callback was called before resource cache was cleared")
          .that(clearResourceCacheAfterFirstBuild.isCacheClean()).isTrue()
    }

    onSourceGenerationError = TestRunnable {
      assertWithMessage("onSourceGenerationError callback was called after resource cache was cleared")
          .that(clearResourceCacheAfterFirstBuild.isCacheClean()).isFalse()
    }
  }

  @Test
  fun isCacheClean_falseBeforeCacheClean() {
    assertThat(clearResourceCacheAfterFirstBuild.isCacheClean()).isFalse()
  }

  @Test
  fun isCacheClean_trueAfterCacheClean() {
    clearResourceCacheAfterFirstBuild.clearResourceCacheIfNecessary()
    assertThat(clearResourceCacheAfterFirstBuild.isCacheClean()).isTrue()
  }

  @Test
  fun runWhenResourceCacheClean_cacheAlreadyCleared() {
    clearResourceCacheAfterFirstBuild.syncSucceeded()

    clearResourceCacheAfterFirstBuild.runWhenResourceCacheClean(onCacheClean, onSourceGenerationError)

    onCacheClean.assertHasRun()
    onSourceGenerationError.assertHasNotRun()
  }

  @Test
  fun runWhenResourceCacheClean_errorAlreadyOccurredAndCacheAlreadyCleared() {
    clearResourceCacheAfterFirstBuild.syncFailed()
    clearResourceCacheAfterFirstBuild.syncSucceeded()

    clearResourceCacheAfterFirstBuild.runWhenResourceCacheClean(onCacheClean, onSourceGenerationError)

    // Ignore errors as long as the resource cache has been cleared once.
    onCacheClean.assertHasRun()
    onSourceGenerationError.assertHasNotRun()
  }

  @Test
  fun runWhenResourceCacheClean_errorAlreadyOccurredButCacheNotCleared() {
    clearResourceCacheAfterFirstBuild.syncFailed()

    clearResourceCacheAfterFirstBuild.runWhenResourceCacheClean(onCacheClean, onSourceGenerationError)
    onCacheClean.assertHasNotRun()
    onSourceGenerationError.assertHasRun()
  }

  @Test
  fun runWhenResourceCacheClean_waits() {
    clearResourceCacheAfterFirstBuild.runWhenResourceCacheClean(onCacheClean, onSourceGenerationError)
    onCacheClean.assertHasNotRun()
    onSourceGenerationError.assertHasNotRun()
  }

  @Test
  fun runWhenResourceCacheClean_errorAlreadyOccurredAndThenCacheClean() {
    clearResourceCacheAfterFirstBuild.syncFailed()
    clearResourceCacheAfterFirstBuild.runWhenResourceCacheClean(onCacheClean, onSourceGenerationError)
    clearResourceCacheAfterFirstBuild.syncSucceeded()

    onCacheClean.assertHasRun()
  }

  @Test
  fun runWhenResourceCacheClean_cacheClearedLater() {
    clearResourceCacheAfterFirstBuild.runWhenResourceCacheClean(onCacheClean, onSourceGenerationError)

    clearResourceCacheAfterFirstBuild.syncSucceeded()
    onCacheClean.assertHasRun()
    onSourceGenerationError.assertHasNotRun()
  }

  @Test
  fun runWhenResourceCacheClean_errorLater() {
    clearResourceCacheAfterFirstBuild.runWhenResourceCacheClean(onCacheClean, onSourceGenerationError)

    clearResourceCacheAfterFirstBuild.syncFailed()
    onCacheClean.assertHasNotRun()
    onSourceGenerationError.assertHasRun()
  }

  @Test
  fun runWhenResourceCacheClean_errorLaterAndThenCacheClean() {
    clearResourceCacheAfterFirstBuild.runWhenResourceCacheClean(onCacheClean, onSourceGenerationError)
    clearResourceCacheAfterFirstBuild.syncFailed()
    clearResourceCacheAfterFirstBuild.syncSucceeded()

    onCacheClean.assertHasRun()
  }
}
