package com.nuvio.app.features.profiles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.features.membership.CosmeticEntitlement
import com.nuvio.app.features.settings.AppBrandWordmark
import com.nuvio.app.features.settings.SupporterBadgeIfPresent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfileSelectionScreen(
    onProfileSelected: (NuvioProfile, Offset) -> Unit,
    onEditProfile: (NuvioProfile) -> Unit,
    onAddProfile: () -> Unit,
    interactionEnabled: Boolean = true,
    contentVisible: Boolean = true,
    onSignInWithAccount: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // Paired with the tapped avatar's on-screen center at the moment of the tap, so once the PIN
    // is verified the caller can still glide the transition emblem out from that exact spot.
    var pendingPinSelection by remember { mutableStateOf<Pair<NuvioProfile, Offset>?>(null) }
    var isEditMode by remember { mutableStateOf(false) }

    val titleAlpha = remember { Animatable(0f) }
    val titleOffset = remember { Animatable(20f) }
    val manageAlpha = remember { Animatable(0f) }
    // Drives the fade-out of everything below the header once a profile is tapped (Netflix-style
    // transition into AppLoadingContent). A plain Animatable rather than AnimatedVisibility's
    // built-in enter/exit so the supporter badge next to the wordmark — rendered as a sibling, not
    // a descendant, of whatever this is applied to — can sit outside it and stay fully visible.
    val contentFadeAlpha = remember { Animatable(1f) }
    val onProfileClick: (NuvioProfile, Offset) -> Unit = { profile, tapCenter ->
        if (interactionEnabled) {
            routeProfileSelection(
                profile = profile,
                isEditMode = isEditMode,
                onEditProfile = onEditProfile,
                onPinRequired = { pendingPinSelection = it to tapCenter },
                onProfileSelected = { onProfileSelected(it, tapCenter) },
            )
        }
    }

    LaunchedEffect(Unit) {
        AvatarRepository.refreshAvatars()
    }

    LaunchedEffect(Unit) {
        launch { titleAlpha.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
        launch { titleOffset.animateTo(0f, tween(600, easing = FastOutSlowInEasing)) }
        delay(300)
        manageAlpha.animateTo(1f, tween(500))
    }

    LaunchedEffect(contentVisible) {
        contentFadeAlpha.animateTo(if (contentVisible) 1f else 0f, tween(180))
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val backgroundProfile = profileState.activeProfile ?: profileState.profiles.firstOrNull()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize(),
    ) {
        val isTabletLayout = maxWidth >= 768.dp
        ProfileBackgroundBackdrop(
            profile = backgroundProfile,
            modifier = Modifier.fillMaxSize(),
        )

        run {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = statusBarTop)
                    .then(
                        if (isTabletLayout) {
                            Modifier
                        } else {
                            Modifier.verticalScroll(rememberScrollState())
                        },
                    )
                    .padding(horizontal = 24.dp),
                verticalArrangement = if (isTabletLayout) Arrangement.Center else Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(if (isTabletLayout) 0.dp else 60.dp))

                // Not wrapped in `contentFadeAlpha` like the rest of this screen below: the
                // wordmark and the Supporter badge next to it both stay fully visible through the
                // tap→center transition, while everything underneath fades away.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppBrandWordmark(
                        modifier = Modifier
                            .height(if (isTabletLayout) 42.dp else 34.dp)
                            .graphicsLayer {
                                alpha = titleAlpha.value
                                translationY = titleOffset.value
                            },
                    )
                    SupporterBadgeIfPresent(
                        height = if (isTabletLayout) 42.dp else 34.dp,
                        modifier = Modifier.graphicsLayer {
                            alpha = titleAlpha.value
                            translationY = titleOffset.value
                        },
                    )
                }

                Spacer(modifier = Modifier.height(if (isTabletLayout) 22.dp else 18.dp))

              Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = contentFadeAlpha.value },
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Text(
                    text = stringResource(Res.string.profile_who_is_watching),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 30.sp,
                        letterSpacing = 0.sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.graphicsLayer {
                        alpha = titleAlpha.value
                        translationY = titleOffset.value
                    },
                )

                Spacer(modifier = Modifier.height(if (isTabletLayout) 28.dp else 48.dp))

                val profiles = profileState.profiles
                val items = profiles.size + if (isEditMode && profiles.size < MAX_PROFILES) 1 else 0

                if (isTabletLayout) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            for (currentIndex in 0 until items) {
                                if (currentIndex < profiles.size) {
                                    val profile = profiles[currentIndex]
                                    ProfileAvatarCard(
                                        profile = profile,
                                        isEditMode = isEditMode,
                                        animDelay = currentIndex * 80,
                                        enabled = interactionEnabled,
                                        onClick = { tapCenter ->
                                            onProfileClick(profile, tapCenter)
                                        },
                                    )
                                } else {
                                    AddProfileCard(
                                        animDelay = currentIndex * 80,
                                        enabled = interactionEnabled,
                                        onClick = onAddProfile,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        var index = 0
                        while (index < items) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                for (col in 0..1) {
                                    if (index < items) {
                                        val currentIndex = index
                                        if (currentIndex < profiles.size) {
                                            val profile = profiles[currentIndex]
                                            ProfileAvatarCard(
                                                profile = profile,
                                                isEditMode = isEditMode,
                                                animDelay = currentIndex * 80,
                                                enabled = interactionEnabled,
                                                onClick = { tapCenter ->
                                                    onProfileClick(profile, tapCenter)
                                                },
                                            )
                                        } else {
                                            AddProfileCard(
                                                animDelay = currentIndex * 80,
                                                enabled = interactionEnabled,
                                                onClick = onAddProfile,
                                            )
                                        }
                                        index++
                                    } else {
                                        if (profiles.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(150.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isTabletLayout) 28.dp else 48.dp))

                Box(
                    modifier = Modifier
                        .graphicsLayer { alpha = manageAlpha.value }
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (isEditMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else Color.Transparent,
                        )
                        .border(
                            width = 1.dp,
                            color = if (isEditMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(24.dp),
                        )
                        .clickable(enabled = interactionEnabled) { isEditMode = !isEditMode }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = if (isEditMode) {
                            stringResource(Res.string.action_done)
                        } else {
                            stringResource(Res.string.profile_manage_profiles)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isEditMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (onSignInWithAccount != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(Res.string.settings_account_sign_in_with_account),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .graphicsLayer { alpha = manageAlpha.value }
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(enabled = interactionEnabled, onClick = onSignInWithAccount)
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                    )
                }

                Spacer(modifier = Modifier.height(if (isTabletLayout) 0.dp else 32.dp))
              }
            }
        }
    }

    pendingPinSelection?.let { (profile, tapCenter) ->
        PinEntryDialog(
            profileName = profile.name,
            onVerify = { pin -> ProfileRepository.verifyPin(profile.profileIndex, pin) },
            onVerified = {
                pendingPinSelection = null
                onProfileSelected(profile, tapCenter)
            },
            onDismiss = { pendingPinSelection = null },
        )
    }
}

@Composable
private fun ProfileAvatarCard(
    profile: NuvioProfile,
    isEditMode: Boolean,
    animDelay: Int,
    enabled: Boolean,
    onClick: (Offset) -> Unit,
) {
    val avatarColor = remember(profile.avatarColorHex) {
        parseHexColor(profile.avatarColorHex)
    }
    val avatars by AvatarRepository.avatars.collectAsStateWithLifecycle()
    val avatarItem = remember(profile.avatarId, avatars) {
        profile.avatarId?.let { id -> avatars.find { it.id == id } }
    }
    val avatarImageUrl = remember(profile.avatarUrl, avatarItem) {
        profileAvatarImageUrl(profile, avatarItem)
    }

    val animAlpha = remember { Animatable(0f) }
    val animScale = remember { Animatable(0.85f) }
    val animOffset = remember { Animatable(30f) }

    LaunchedEffect(Unit) {
        delay(animDelay.toLong() + 150)
        launch { animAlpha.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
        launch { animScale.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
        launch { animOffset.animateTo(0f, tween(500, easing = FastOutSlowInEasing)) }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale = if (isPressed) 0.95f else 1f

    // Kept up to date on every layout pass so the tap handler below can hand back exactly where
    // on screen this avatar sits — that's where AppLoadingContent glides its emblem in from.
    var avatarCenterInWindow by remember { mutableStateOf(Offset.Zero) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(150.dp)
            .graphicsLayer {
                alpha = animAlpha.value
                scaleX = animScale.value * pressScale
                scaleY = animScale.value * pressScale
                translationY = animOffset.value
            }
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(avatarCenterInWindow) },
            )
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .onGloballyPositioned { coordinates ->
                    avatarCenterInWindow = coordinates.positionInWindow() + Offset(
                        coordinates.size.width / 2f,
                        coordinates.size.height / 2f,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (avatarImageUrl != null) {
                val bgColor = avatarItem?.bgColor?.let { parseHexColor(it) } ?: avatarColor
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(bgColor.copy(alpha = 0.2f)),
                )
            }

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        if (avatarItem != null) {
                            avatarItem.bgColor?.let { parseHexColor(it) } ?: avatarColor
                        } else {
                            avatarColor.copy(alpha = 0.15f)
                        },
                    )
                    .then(
                        if (avatarImageUrl == null) Modifier.border(2.dp, avatarColor.copy(alpha = 0.4f), CircleShape)
                        else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarImageUrl != null) {
                    NuvioAsyncImage(
                        imageUrl = avatarImageUrl,
                        contentDescription = avatarItem?.displayName ?: profile.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        animateIfPossible = true,
                    )
                } else if (profile.name.isNotBlank()) {
                    Text(
                        text = profile.name.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 38.sp),
                        color = avatarColor,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = avatarColor,
                        modifier = Modifier.size(46.dp),
                    )
                }
            }

            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            if (profile.pinEnabled && !isEditMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = profile.name.ifBlank {
                stringResource(Res.string.profile_label_number, profile.profileIndex)
            },
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AddProfileCard(
    animDelay: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val animAlpha = remember { Animatable(0f) }
    val animScale = remember { Animatable(0.85f) }
    val animOffset = remember { Animatable(30f) }

    LaunchedEffect(Unit) {
        delay(animDelay.toLong() + 150)
        launch { animAlpha.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
        launch { animScale.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
        launch { animOffset.animateTo(0f, tween(500, easing = FastOutSlowInEasing)) }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale = if (isPressed) 0.95f else 1f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(150.dp)
            .graphicsLayer {
                alpha = animAlpha.value
                scaleX = animScale.value * pressScale
                scaleY = animScale.value * pressScale
                translationY = animOffset.value
            }
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier.size(110.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(Res.string.compose_profile_add_profile),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}
