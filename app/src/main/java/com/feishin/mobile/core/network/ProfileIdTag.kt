package com.feishin.mobile.core.network

/**
 * OkHttp request tag carrying the originating [com.feishin.mobile.domain.model.ServerProfile]
 * (or transient probe) ID so interceptors can isolate multiple profiles sharing the same host.
 */
data class ProfileIdTag(val profileId: String)
