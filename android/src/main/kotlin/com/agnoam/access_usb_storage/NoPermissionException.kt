package com.agnoam.access_usb_storage

class NoPermissionException(
    _tag: String, _deviceKey: String,
    _error: Any, _errorInfo: Any
) : Exception() {
    private val tag: String = _tag
    private val deviceKey: String = _deviceKey
    private val error: Any = _error
    private val errorInfo: Any = _errorInfo
}