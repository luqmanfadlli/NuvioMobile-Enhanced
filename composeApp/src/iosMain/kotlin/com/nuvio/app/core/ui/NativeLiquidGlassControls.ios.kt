package com.nuvio.app.core.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIButton
import platform.UIKit.accessibilityLabel
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIControlEventEditingChanged
import platform.UIKit.UIControlEventEditingDidBegin
import platform.UIKit.UIControlEventEditingDidEnd
import platform.UIKit.UIControlEventEditingDidEndOnExit
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIImage
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UILongPressGestureRecognizer
import platform.UIKit.UISearchBar
import platform.UIKit.UISearchBarStyle
import platform.UIKit.UISegmentedControl
import platform.UIKit.UISwitch
import platform.UIKit.UITextFieldViewMode
import platform.UIKit.UIReturnKeyType
import com.nuvio.app.core.ui.glass.LiquidGlassSettings
import com.nuvio.app.features.settings.ThemeSettingsRepository

@OptIn(ExperimentalForeignApi::class)
internal actual fun isNativeLiquidGlassControlsAvailable(): Boolean {
    val majorVersion = NSProcessInfo.processInfo.operatingSystemVersion.useContents {
        majorVersion.toLong()
    }
    val appBackgroundSet = ThemeSettingsRepository.customSettingsBackgroundImageUri.value != null
    return LiquidGlassSettings.surfaceOverrides.experimentalEnabled &&
        !appBackgroundSet &&
        shouldUseNativeLiquidGlassControls(majorVersion)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun isNativeLiquidGlassControlsSupportedByOs(): Boolean {
    val majorVersion = NSProcessInfo.processInfo.operatingSystemVersion.useContents {
        majorVersion.toLong()
    }
    return shouldUseNativeLiquidGlassControls(majorVersion)
}

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
internal actual fun NuvioNativeLiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accessibilityLabel: String,
    modifier: Modifier,
) {
    UIKitView(
        factory = {
            LiquidGlassNativeSwitch().apply { setOn(checked, animated = false) }
        },
        modifier = modifier.size(width = 51.dp, height = 31.dp),
        update = { nativeSwitch ->
            nativeSwitch.valueChangedListener = onCheckedChange
            nativeSwitch.accessibilityLabel = accessibilityLabel
            if (nativeSwitch.on != checked) {
                nativeSwitch.setOn(checked, animated = false)
            }
        },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.Cooperative(),
            isNativeAccessibilityEnabled = true,
        ),
    )
}

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
internal actual fun NuvioNativeLiquidGlassSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelectionChanged: (Int) -> Unit,
    modifier: Modifier,
) {
    UIKitView(
        factory = {
            LiquidGlassNativeSegmentedControl(items).apply {
                setSelectedSegmentIndex(selectedIndex.toLong())
            }
        },
        modifier = modifier.height(32.dp),
        update = { nativeControl ->
            nativeControl.updateItems(items)
            nativeControl.selectionChangedListener = onSelectionChanged
            nativeControl.accessibilityLabel = items.joinToString()
            if (nativeControl.selectedSegmentIndex.toInt() != selectedIndex) {
                nativeControl.setSelectedSegmentIndex(selectedIndex.toLong())
            }
        },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.Cooperative(),
            isNativeAccessibilityEnabled = true,
        ),
    )
}

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
internal actual fun NuvioNativeLiquidGlassButton(
    title: String?,
    systemImage: String?,
    accessibilityLabel: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
) {
    UIKitView(
        factory = {
            LiquidGlassNativeButton().apply {
                update(title, systemImage, accessibilityLabel, selected, enabled, onClick, onLongClick)
            }
        },
        modifier = modifier,
        update = { button ->
            button.update(title, systemImage, accessibilityLabel, selected, enabled, onClick, onLongClick)
        },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.Cooperative(),
            isNativeAccessibilityEnabled = true,
        ),
    )
}

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
internal actual fun NuvioNativeLiquidGlassSearchField(
    value: String,
    placeholder: String,
    accessibilityLabel: String,
    focusRequestCount: Int,
    shouldBeFocused: Boolean,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier,
) {
    UIKitView(
        factory = {
            LiquidGlassNativeSearchBar().apply {
                update(value, placeholder, accessibilityLabel, focusRequestCount, shouldBeFocused, onValueChange, onSearch, onFocusChanged)
            }
        },
        modifier = modifier.height(48.dp),
        update = { searchBar ->
            searchBar.update(value, placeholder, accessibilityLabel, focusRequestCount, shouldBeFocused, onValueChange, onSearch, onFocusChanged)
        },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.Cooperative(),
            isNativeAccessibilityEnabled = true,
        ),
    )
}

@OptIn(ExperimentalForeignApi::class)
private class LiquidGlassNativeSwitch : UISwitch(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    var valueChangedListener: ((Boolean) -> Unit)? = null
    init {
        addTarget(this, NSSelectorFromString("handleValueChanged"), UIControlEventValueChanged)
    }
    @ObjCAction
    fun handleValueChanged() { valueChangedListener?.invoke(on) }
}

@OptIn(ExperimentalForeignApi::class)
private class LiquidGlassNativeSegmentedControl(
    initialItems: List<String>,
) : UISegmentedControl(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private var itemTitles: List<String> = emptyList()
    var selectionChangedListener: ((Int) -> Unit)? = null
    init {
        updateItems(initialItems)
        addTarget(this, NSSelectorFromString("handleValueChanged"), UIControlEventValueChanged)
    }
    fun updateItems(items: List<String>) {
        if (itemTitles == items) return
        removeAllSegments()
        items.forEachIndexed { index, title ->
            insertSegmentWithTitle(title, index.toULong(), false)
        }
        itemTitles = items.toList()
    }
    @ObjCAction
    fun handleValueChanged() { selectionChangedListener?.invoke(selectedSegmentIndex.toInt()) }
}

@OptIn(ExperimentalForeignApi::class)
private class LiquidGlassNativeButton : UIButton(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private var clickListener: () -> Unit = {}
    private var longClickListener: (() -> Unit)? = null
    private var lastTitle: String? = null
    private var lastSystemImage: String? = null
    private var lastSelected: Boolean? = null
    private var lastEnabled: Boolean? = null
    private var lastAccessibilityLabel: String? = null
    private val longPressRecognizer = UILongPressGestureRecognizer(
        target = this,
        action = NSSelectorFromString("handleLongPress:"),
    )
    init {
        addTarget(this, NSSelectorFromString("handleTap"), UIControlEventTouchUpInside)
        // A recognized long press must not also trigger UIControlEventTouchUpInside.
        longPressRecognizer.cancelsTouchesInView = true
        longPressRecognizer.delaysTouchesBegan = false
        longPressRecognizer.delaysTouchesEnded = false
        longPressRecognizer.minimumPressDuration = 0.48
        addGestureRecognizer(longPressRecognizer)
    }
    fun update(
        title: String?,
        systemImage: String?,
        accessibilityLabel: String,
        selected: Boolean,
        enabled: Boolean,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)?,
    ) {
        clickListener = onClick
        longClickListener = onLongClick
        longPressRecognizer.enabled = enabled && onLongClick != null
        if (lastEnabled != enabled) {
            this.enabled = enabled
            lastEnabled = enabled
        }
        if (lastAccessibilityLabel != accessibilityLabel) {
            this.accessibilityLabel = accessibilityLabel
            lastAccessibilityLabel = accessibilityLabel
        }
        if (lastTitle != title || lastSystemImage != systemImage || lastSelected != selected) {
            val config = if (selected) {
                UIButtonConfiguration.prominentGlassButtonConfiguration()
            } else {
                UIButtonConfiguration.glassButtonConfiguration()
            }
            config.title = title
            config.image = systemImage?.let { UIImage.systemImageNamed(it) }
            configuration = config
            lastTitle = title
            lastSystemImage = systemImage
            lastSelected = selected
        }
    }
    @ObjCAction
    fun handleTap() { if (this.enabled) clickListener() }
    @ObjCAction
    fun handleLongPress(recognizer: UILongPressGestureRecognizer) {
        if (recognizer.state == UIGestureRecognizerStateBegan && enabled) longClickListener?.invoke()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class LiquidGlassNativeSearchBar : UISearchBar(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    // Kotlin/Native 2.4.10 retains full C names for strict UIKit NS_ENUM entries.
    private var valueListener: (String) -> Unit = {}
    private var searchListener: () -> Unit = {}
    private var focusListener: (Boolean) -> Unit = {}
    private var lastFocusRequestCount = 0
    private var wasComposeFocused = false
    init {
        searchBarStyle = UISearchBarStyle.UISearchBarStyleMinimal
        setShowsCancelButton(false, animated = false)
        searchTextField.returnKeyType = UIReturnKeyType.UIReturnKeySearch
        searchTextField.clearButtonMode = UITextFieldViewMode.UITextFieldViewModeAlways
        searchTextField.addTarget(this, NSSelectorFromString("handleTextChanged"), UIControlEventEditingChanged)
        searchTextField.addTarget(this, NSSelectorFromString("handleEditingBegan"), UIControlEventEditingDidBegin)
        searchTextField.addTarget(this, NSSelectorFromString("handleEditingEnded"), UIControlEventEditingDidEnd)
        searchTextField.addTarget(this, NSSelectorFromString("handleSearchSubmitted"), UIControlEventEditingDidEndOnExit)
    }
    fun update(
        value: String,
        placeholder: String,
        accessibilityLabel: String,
        focusRequestCount: Int,
        shouldBeFocused: Boolean,
        onValueChange: (String) -> Unit,
        onSearch: () -> Unit,
        onFocusChanged: (Boolean) -> Unit,
    ) {
        valueListener = onValueChange
        searchListener = onSearch
        focusListener = onFocusChanged
        this.placeholder = placeholder
        searchTextField.accessibilityLabel = accessibilityLabel
        if (searchTextField.text != value) searchTextField.text = value
        if (focusRequestCount > lastFocusRequestCount) {
            lastFocusRequestCount = focusRequestCount
            wasComposeFocused = true
            searchTextField.becomeFirstResponder()
        } else if (shouldBeFocused) {
            // Do not resign during the brief interval between UIKit beginning editing and the
            // Compose focus callback reaching the next recomposition.
            wasComposeFocused = true
        } else if (wasComposeFocused) {
            wasComposeFocused = false
            if (searchTextField.isFirstResponder) {
                searchTextField.resignFirstResponder()
            }
        }
    }
    @ObjCAction
    fun handleTextChanged() { valueListener(searchTextField.text.orEmpty()) }
    @ObjCAction
    fun handleEditingBegan() { focusListener(true) }
    @ObjCAction
    fun handleEditingEnded() { focusListener(false) }
    @ObjCAction
    fun handleSearchSubmitted() {
        searchListener()
        searchTextField.resignFirstResponder()
    }
}
