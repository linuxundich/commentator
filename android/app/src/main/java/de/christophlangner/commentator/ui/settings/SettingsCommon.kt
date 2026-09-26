package de.christophlangner.commentator.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.christophlangner.commentator.R
import de.christophlangner.commentator.domain.model.NotifyScope
import de.christophlangner.commentator.domain.model.RoleAccent
import de.christophlangner.commentator.domain.model.RoleStyle
import de.christophlangner.commentator.domain.model.Team
import de.christophlangner.commentator.domain.model.TeamRole
import de.christophlangner.commentator.domain.repository.ReplyTemplate
import de.christophlangner.commentator.ui.theme.roleColors

/*
 * Bausteine, die beide Einstellungsbildschirme brauchen.
 *
 * Die App hat zwei: einen für das, was für alle Blogs gilt, und einen je
 * Blog. Die Zeilen, Schalter und Dialoge sind dieselben - und sollen es
 * bleiben, damit die beiden Bildschirme nicht auseinanderlaufen.
 *
 * Alles `internal`: Es gehört zu diesem Paket und wird von den Tests
 * unmittelbar geprüft, aber nichts davon ist Teil der Oberfläche nach außen.
 */

@Composable
internal fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val farbe = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // Die ganze Zeile schaltet, damit das Berührungsziel groß genug ist.
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = farbe)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else farbe,
            )
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

internal fun NotifyScope.labelRes(): Int = when (this) {
    NotifyScope.PENDING -> R.string.settings_scope_pending
    NotifyScope.NEW_COMMENTS -> R.string.settings_scope_new
    NotifyScope.EVERYTHING -> R.string.settings_scope_everything
}

// --- Antwortvorlagen ---

@Composable
internal fun TemplateRow(
    template: ReplyTemplate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Text(
            text = template.text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.templates_delete),
            )
        }
    }
}

/**
 * Dialog zum Anlegen und Bearbeiten.
 *
 * Derselbe Dialog fuer beides: Der Unterschied ist allein, ob ein Text
 * vorbelegt ist.
 */
@Composable
internal fun TemplateDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initialText.isEmpty()) R.string.templates_add else R.string.templates_edit,
                ),
            )
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.templates_text_label)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

// --- Rollen ---

/**
 * Die Rollen, die sich einstellen lassen.
 *
 * Die vier Standardrollen stehen immer da, auch wenn der Blog sie nicht
 * meldet - ohne das Bridge-Plugin tut er das nie, und ein leerer Abschnitt
 * waere das Gegenteil von einstellbar. Gemeldete Namen haben Vorrang: Ein
 * Blog darf seine Rollen umbenennen.
 */
@Composable
internal fun konfigurierbareRollen(gemeldet: List<TeamRole>): List<TeamRole> {
    val nachSlug = gemeldet.associateBy { it.slug }
    val standard = Team.STANDARD_ROLES.map { slug ->
        nachSlug[slug] ?: TeamRole(slug, stringResource(standardRoleNameRes(slug)))
    }
    // Das eigene Konto immer zuerst: Ohne Plugin ist es das einzige erkannte
    // Mitglied, und die eigenen Antworten sind ohnehin der haeufigste Fall.
    val eigenes = TeamRole(Team.SELF, stringResource(R.string.role_self))
    // Eigene Rollen eines Blogs sind die Ausnahme und stehen deshalb hinten.
    return listOf(eigenes) + standard + gemeldet.filterNot { it.slug in Team.STANDARD_ROLES }
}

private fun standardRoleNameRes(slug: String): Int = when (slug) {
    Team.ADMINISTRATOR -> R.string.role_administrator
    Team.EDITOR -> R.string.role_editor
    Team.AUTHOR -> R.string.role_author
    else -> R.string.role_contributor
}

/**
 * Eine Rolle in der Uebersicht.
 *
 * Die Zeile fasst zusammen, was eingestellt ist - Farbe als Tupfer, der Rest
 * als Satz. Wer mehr will, tippt sie an.
 */
@Composable
internal fun RoleRow(
    role: TeamRole,
    isTeam: Boolean,
    style: RoleStyle,
    onClick: () -> Unit,
) {
    val farben = roleColors(style.accent)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (isTeam && style.colorEnabled) {
                        farben.badge
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(text = role.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = roleSummary(isTeam = isTeam, style = style),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Was fuer diese Rolle gilt, in einem Satz. */
@Composable
private fun roleSummary(isTeam: Boolean, style: RoleStyle): String {
    if (!isTeam) return stringResource(R.string.role_summary_not_team)

    val teile = buildList {
        add(
            stringResource(
                if (style.showInTimeline) {
                    R.string.role_summary_visible
                } else {
                    R.string.role_summary_collapsed
                },
            ),
        )
        if (!style.colorEnabled) add(stringResource(R.string.role_summary_no_color))
        if (!style.notify) add(stringResource(R.string.role_summary_muted))
    }
    return teile.joinToString(" · ")
}

/**
 * Die Einstellungen einer Rolle.
 *
 * Alles, was zu einer Rolle gehoert, an einer Stelle - und jede Aenderung
 * wirkt sofort, ohne Bestaetigen. Ein Dialog mit "Speichern" wuerde hier nur
 * einen Handgriff hinzufuegen: Zurueckgenommen ist jeder Schalter genauso
 * schnell, wie er umgelegt war.
 *
 * `internal` und nicht privat, damit er sich ohne Hilt pruefen laesst - wie
 * der uebrige Inhalt dieses Bildschirms.
 */
@Composable
internal fun RoleDialog(
    role: TeamRole,
    isTeam: Boolean,
    /** Ob sich die Rolle aus dem Team nehmen laesst - beim eigenen Konto nicht. */
    canLeaveTeam: Boolean,
    style: RoleStyle,
    onToggleTeam: () -> Unit,
    onStyleChange: (RoleStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(role.name) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (canLeaveTeam) {
                    DialogSwitch(
                        title = stringResource(R.string.role_is_team),
                        description = stringResource(R.string.role_is_team_description),
                        checked = isTeam,
                        enabled = true,
                        onCheckedChange = { onToggleTeam() },
                    )
                } else {
                    // Das eigene Konto aus dem Team zu nehmen ginge nicht: Die
                    // App erkennt es auch ohne Plugin, und ein Schalter, der
                    // nichts bewirkt, waere schlimmer als keiner.
                    Text(
                        text = stringResource(R.string.role_self_always_team),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Alles Weitere gilt nur fuer Rollen, die als Team gefuehrt
                // werden: Wer nicht zum Team gehoert, hat weder Farbe noch
                // eigene Benachrichtigung - seine Kommentare sind die, um die
                // es beim Moderieren geht.
                DialogSwitch(
                    title = stringResource(R.string.role_color),
                    description = stringResource(R.string.role_color_description),
                    checked = style.colorEnabled,
                    enabled = isTeam,
                    onCheckedChange = { onStyleChange(style.copy(colorEnabled = it)) },
                )

                if (isTeam && style.colorEnabled) {
                    AccentPicker(
                        selected = style.accent,
                        onSelect = { onStyleChange(style.copy(accent = it)) },
                    )
                }

                DialogSwitch(
                    title = stringResource(R.string.role_timeline),
                    description = stringResource(R.string.role_timeline_description),
                    checked = style.showInTimeline,
                    enabled = isTeam,
                    onCheckedChange = { onStyleChange(style.copy(showInTimeline = it)) },
                )

                DialogSwitch(
                    title = stringResource(R.string.role_notify),
                    description = stringResource(R.string.role_notify_description),
                    checked = style.notify,
                    enabled = isTeam,
                    onCheckedChange = { onStyleChange(style.copy(notify = it)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_done))
            }
        },
    )
}

/**
 * Die Farbauswahl.
 *
 * Jeder Ton zeigt sich selbst - ein Name allein saegt nichts darueber aus,
 * wie blass oder kraeftig er in der Liste wirkt. Der gewaehlte traegt einen
 * Ring, denn allein durch die Farbe waere die Auswahl fuer farbfehlsichtige
 * Augen nicht zu erkennen.
 */
@Composable
private fun AccentPicker(selected: RoleAccent, onSelect: (RoleAccent) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        RoleAccent.entries.forEach { accent ->
            val farben = roleColors(accent)
            val gewaehlt = accent == selected
            val name = stringResource(accent.labelRes())
            // Das Beruehrungsziel ist 48 dp gross, der Tupfer darin 40 -
            // sonst waere die Auswahl auf einem Telefon kaum zu treffen.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .selectable(
                        selected = gewaehlt,
                        role = Role.RadioButton,
                        onClick = { onSelect(accent) },
                    )
                    .semantics { contentDescription = name },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(farben.badge)
                        .border(
                            width = if (gewaehlt) 3.dp else 1.dp,
                            color = if (gewaehlt) {
                                farben.onRole
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        ),
                ) {
                    if (gewaehlt) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = farben.onRole,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ein Schalter im Dialog.
 *
 * Wie [SwitchRow], nur schmaler: Im Dialog ist weniger Platz, und was ohne
 * Team-Kennzeichnung keine Wirkung haette, steht ausgegraut da statt zu
 * verschwinden - sonst huepfte der Dialog bei jedem Umlegen.
 */
@Composable
private fun DialogSwitch(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val farbe = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = farbe)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    farbe
                },
            )
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

private fun RoleAccent.labelRes(): Int = when (this) {
    RoleAccent.SCHIEFER -> R.string.accent_schiefer
    RoleAccent.INDIGO -> R.string.accent_indigo
    RoleAccent.TEAL -> R.string.accent_teal
    RoleAccent.MOOS -> R.string.accent_moos
    RoleAccent.OCKER -> R.string.accent_ocker
    RoleAccent.TERRAKOTTA -> R.string.accent_terrakotta
    RoleAccent.PFLAUME -> R.string.accent_pflaume
}
