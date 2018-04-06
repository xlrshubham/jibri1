/*
 * Copyright @ 2018 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.jitsi.jibri.api.http

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.anyOrNull
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.kotlintest.Description
import io.kotlintest.Matcher
import io.kotlintest.Result
import io.kotlintest.TestResult
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldNot
import io.kotlintest.specs.ShouldSpec
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.test.JerseyTest
import org.jitsi.jibri.CallParams
import org.jitsi.jibri.CallUrlInfo
import org.jitsi.jibri.JibriManager
import org.jitsi.jibri.RecordingSinkType
import org.jitsi.jibri.ServiceParams
import org.jitsi.jibri.StartServiceResult
import org.jitsi.jibri.config.XmppCredentials
import org.jitsi.jibri.health.EnvironmentContext
import org.jitsi.jibri.health.JibriHealth
import javax.ws.rs.client.Entity
import javax.ws.rs.core.Application
import javax.ws.rs.ext.ContextResolver

fun containStr(str: String) = object : Matcher<String> {
    override fun test(value: String) = Result(
        value.contains(str),
        "String $value should contain $str",
        "String $value should not contain $str")
}

class HttpApiTest : ShouldSpec() {
    private val jibriManager: JibriManager = mock()
    private lateinit var jerseyTest: JerseyTest

    override fun beforeTest(description: Description) {
        super.beforeTest(description)
        jerseyTest = object : JerseyTest() {
            override fun configure(): Application {
                return ResourceConfig(object : ResourceConfig() {
                    init {
                        register(ContextResolver<ObjectMapper> { ObjectMapper().registerKotlinModule() })
                        registerInstances(HttpApi(jibriManager))
                    }
                })
            }
        }
        jerseyTest.setUp()
    }

    override fun afterTest(description: Description, result: TestResult) {
        super.afterTest(description, result)
        jerseyTest.tearDown()
    }

    init {
        "health" {
            "when jibri isn't busy" {
                val expectedHealth = JibriHealth(busy = false)
                whenever(jibriManager.healthCheck())
                    .thenReturn(expectedHealth)
                should("call JibriManager#healthCheck") {
                    jerseyTest.target("/jibri/api/v1.0/health").request().get()
                    verify(jibriManager).healthCheck()
                }
                should("return the right json body and a status of 200") {
                    val res = jerseyTest.target("/jibri/api/v1.0/health").request()
                        .get()
                    res.status shouldBe 200
                    val json = res.readEntity(String::class.java)
                    // The json should not include the 'environmentContext' field at all, since it
                    // will be null
                    json shouldNot containStr("environmentContext")
                    val health = jacksonObjectMapper().readValue(json, JibriHealth::class.java)
                    health shouldBe expectedHealth
                }
            }
            "when jibri is busy and has an environmentContext" {
                val expectedHealth = JibriHealth(
                    busy = true,
                    environmentContext = EnvironmentContext("meet.jit.si")
                )
                whenever(jibriManager.healthCheck())
                    .thenReturn(expectedHealth)
                should("return the right json body and a status of 200") {
                    val res = jerseyTest.target("/jibri/api/v1.0/health").request()
                        .get()
                    res.status shouldBe 200
                    val json = res.readEntity(String::class.java)
                    // The json should not include the 'environmentContext' field at all, since it
                    // will be null
                    val health = jacksonObjectMapper().readValue(json, JibriHealth::class.java)
                    health shouldBe expectedHealth
                }
            }
        }
        "startService" {
            "start file recording" {
                val capturedServiceParams = argumentCaptor<ServiceParams>()
                whenever(jibriManager.startFileRecording(
                    capturedServiceParams.capture(),
                    any(),
                    anyOrNull(),
                    anyOrNull())
                ).thenReturn(StartServiceResult.SUCCESS)
                val startServiceRequest = StartServiceParams(
                    callParams = CallParams(
                        callUrlInfo = CallUrlInfo("https://meet.jit.si", "callName"),
                        callLoginParams = XmppCredentials(
                            domain = "xmpp_domain",
                            username = "xmpp_username",
                            password = "xmpp_password"
                        )
                    ),
                    sinkType = RecordingSinkType.FILE
                )
                val json = jacksonObjectMapper().writeValueAsString(startServiceRequest)
                should("return a 200") {
                    val res = jerseyTest
                        .target("/jibri/api/v1.0/startService")
                        .request()
                        .post(Entity.json(json))
                    res.status shouldBe 200
                    println(res.readEntity(String::class.java))
                    capturedServiceParams.firstValue.usageTimeoutMinutes shouldBe 0
                }
            }
        }
    }
}
