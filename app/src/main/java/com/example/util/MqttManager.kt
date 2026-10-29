package com.example.util

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.UUID

/**
 * Enterprise Realtime MQTT Client for Zero-Cost High-Frequency Fleet Telemetry.
 * Bypasses Firestore write limits by streaming per-second coordinate pings via lightweight MQTT packets.
 */
object MqttManager {
    private const val TAG = "MqttManager"
    private const val PRIMARY_BROKER_URI = "tcp://broker.hivemq.com:1883"
    private const val FALLBACK_BROKER_URI = "tcp://broker.emqx.io:1883"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var client: MqttAsyncClient? = null
    private var currentCompanyId: String? = null
    private var subscribedTopic: String? = null
    private var messageListener: ((topic: String, payload: String) -> Unit)? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _brokerHost = MutableStateFlow("HiveMQ (Free Broker)")
    val brokerHost: StateFlow<String> = _brokerHost.asStateFlow()

    private val _packetCounter = MutableStateFlow(0)
    val packetCounter: StateFlow<Int> = _packetCounter.asStateFlow()

    fun initialize(companyId: String? = null) {
        if (companyId != null) {
            currentCompanyId = companyId
        }
        if (client != null && client?.isConnected == true) {
            Log.d(TAG, "MqttClient already connected")
            return
        }
        connectClient(PRIMARY_BROKER_URI)
    }

    private fun connectClient(brokerUri: String) {
        scope.launch {
            try {
                val clientId = "tracepath_" + UUID.randomUUID().toString().take(10)
                val persistence = MemoryPersistence()
                val mqttClient = MqttAsyncClient(brokerUri, clientId, persistence)
                client = mqttClient

                val options = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    isCleanSession = true
                    connectionTimeout = 10
                    keepAliveInterval = 30
                    serverURIs = arrayOf(PRIMARY_BROKER_URI, FALLBACK_BROKER_URI)
                }

                mqttClient.setCallback(object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        Log.i(TAG, "MQTT Connected to $serverURI (reconnect=$reconnect)")
                        _isConnected.value = true
                        _brokerHost.value = if (serverURI?.contains("emqx") == true) "EMQX Free Cloud" else "HiveMQ Free Cloud"
                        resubscribeIfNecessary()
                    }

                    override fun connectionLost(cause: Throwable?) {
                        Log.w(TAG, "MQTT Connection lost: ${cause?.message}")
                        _isConnected.value = false
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        if (topic != null && message != null) {
                            val payload = String(message.payload, Charsets.UTF_8)
                            _packetCounter.value += 1
                            messageListener?.invoke(topic, payload)
                        }
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {
                        // Message successfully delivered
                    }
                })

                mqttClient.connect(options, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i(TAG, "MQTT Connect success")
                        _isConnected.value = true
                        resubscribeIfNecessary()
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e(TAG, "MQTT Connect failed: ${exception?.message}")
                        _isConnected.value = false
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing MQTT: ${e.message}")
                _isConnected.value = false
            }
        }
    }

    private fun resubscribeIfNecessary() {
        val topic = subscribedTopic ?: currentCompanyId?.let { "geotrack/$it/driver/+/location" }
        if (topic != null && client?.isConnected == true) {
            try {
                client?.subscribe(topic, 0, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i(TAG, "Subscribed successfully to $topic")
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e(TAG, "Failed to subscribe to $topic: ${exception?.message}")
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error subscribing: ${e.message}")
            }
        }
    }

    /**
     * Subscribe to all drivers in a company for Admin real-time tracking
     */
    fun subscribeArmada(companyId: String, onMessage: (topic: String, payload: String) -> Unit) {
        currentCompanyId = companyId
        subscribedTopic = "geotrack/$companyId/driver/+/location"
        messageListener = onMessage

        if (client?.isConnected == true) {
            resubscribeIfNecessary()
        } else {
            initialize(companyId)
        }
    }

    /**
     * Publish driver's coordinate to the MQTT broker (Zero Firestore Write Cost!)
     */
    fun publishLocation(
        companyId: String,
        userId: String,
        fullName: String,
        unitCode: String,
        latitude: Double,
        longitude: Double,
        speedKmh: Float,
        bearing: Float,
        batteryLevel: Int,
        isCharging: Boolean,
        geofenceStatus: String,
        isMock: Boolean
    ) {
        scope.launch {
            try {
                if (client == null || client?.isConnected != true) {
                    initialize(companyId)
                }

                val topic = "geotrack/$companyId/driver/$userId/location"
                val json = JSONObject().apply {
                    put("userId", userId)
                    put("companyId", companyId)
                    put("fullName", fullName)
                    put("unitCode", unitCode)
                    put("latitude", latitude)
                    put("longitude", longitude)
                    put("speedKmh", speedKmh.toDouble())
                    put("bearing", bearing.toDouble())
                    put("batteryLevel", batteryLevel)
                    put("isCharging", isCharging)
                    put("geofenceStatus", geofenceStatus)
                    put("isMock", isMock)
                    put("isSharingLocation", true)
                    put("timestamp", System.currentTimeMillis())
                }

                val message = MqttMessage(json.toString().toByteArray(Charsets.UTF_8)).apply {
                    qos = 0 // QoS 0 for lowest latency and zero broker overhead
                    isRetained = false
                }

                client?.publish(topic, message, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        _packetCounter.value += 1
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.w(TAG, "Failed to publish MQTT message: ${exception?.message}")
                    }
                })
            } catch (e: Exception) {
                Log.w(TAG, "Publish error: ${e.message}")
            }
        }
    }

    fun disconnect() {
        scope.launch {
            try {
                if (client?.isConnected == true) {
                    client?.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Disconnect error: ${e.message}")
            }
        }
    }
}
