package com.diegoca.gpsmocker

import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Build
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class AppSheets(private val activity: AppCompatActivity, private val dataModel: UserDataViewModel) {
    private val dialogs = mutableListOf<Dialog>()
    private var coordinateDialog: Dialog? = null
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun string(id: Int) = activity.getString(id)
    private fun color(attr: Int) = MaterialColors.getColor(activity, attr, "GPS Mocker")
    private val primary get() = color(com.google.android.material.R.attr.colorPrimary)
    private val muted get() = color(com.google.android.material.R.attr.colorOnSurfaceVariant)

    private fun column(padding: Int = 20) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(padding), dp(12), dp(padding), dp(20))
    }

    private fun label(text: CharSequence, size: Float = 14f, secondary: Boolean = true) = TextView(activity).apply {
        this.text = text; textSize = size
        setTextColor(if (secondary) muted else color(com.google.android.material.R.attr.colorOnSurface))
        setPadding(0, dp(4), 0, dp(8))
    }

    private fun button(id: Int, icon: Int? = null, tonal: Boolean = false, textOnly: Boolean = false) = MaterialButton(activity).apply {
        setText(id)
        minHeight = dp(48); cornerRadius = dp(16)
        insetTop = dp(4); insetBottom = dp(4)
        icon?.let { setIconResource(it); iconSize = dp(20) }
        if (tonal || textOnly) {
            backgroundTintList = ColorStateList.valueOf(if (textOnly) android.graphics.Color.TRANSPARENT
                else color(com.google.android.material.R.attr.colorSecondaryContainer))
            setTextColor(primary); iconTint = ColorStateList.valueOf(primary)
        }
    }

    private fun sheet(
        title: Int,
        scroll: Boolean = false,
        onDismissRequested: () -> Unit = {}
    ): Pair<BottomSheetDialog, LinearLayout> {
        val dialog = object : BottomSheetDialog(activity) {
            override fun dismiss() {
                // Dialog's OnDismissListener is posted to the main loop. Clear drafts
                // before a recreation can save them again after an explicit dismissal.
                onDismissRequested()
                super.dismiss()
            }
        }
        val content = column()
        val heading = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        heading.addView(label(string(title), 22f, false), LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(button(R.string.close, R.drawable.ic_close, textOnly = true).apply {
            text = ""; contentDescription = string(R.string.close); setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        content.addView(heading)
        val body = column(0).apply { setPadding(0, dp(4), 0, 0) }
        if (scroll) content.addView(ScrollView(activity).apply { addView(body) }, LinearLayout.LayoutParams(-1, -2))
        else content.addView(body)
        dialog.setContentView(content)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.setOnDismissListener { dialogs.remove(dialog) }
        dialogs += dialog
        return dialog to body
    }

    private fun show(dialog: Dialog) {
        if (dialog !in dialogs) {
            dialogs += dialog
            dialog.setOnDismissListener { dialogs.remove(dialog) }
        }
        dialog.show()
        if (dialog is BottomSheetDialog) {
            dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            dialog.behavior.skipCollapsed = true
            dialog.behavior.maxHeight = (activity.resources.displayMetrics.heightPixels * 0.92).toInt()
        }
    }

    private fun input(parent: LinearLayout, hint: Int, value: String = "", numeric: Boolean = false): TextInputEditText {
        val layout = TextInputLayout(activity).apply {
            this.hint = string(hint)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat())
        }
        val edit = TextInputEditText(layout.context).apply {
            setText(value); setSingleLine(true)
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minHeight = dp(56)
        }
        layout.addView(edit)
        parent.addView(layout, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(8) })
        return edit
    }

    fun coordinates(
        draft: CoordinateDraft,
        onDraftChanged: (CoordinateDraft) -> Unit,
        onChoose: (Coordinates, (Boolean) -> Unit) -> Unit,
        onDismiss: () -> Unit
    ) {
        val (dialog, body) = sheet(R.string.enter_coordinates, scroll = true, onDismissRequested = {
            coordinateDialog = null
            onDismiss()
        })
        coordinateDialog = dialog
        body.addView(label(string(R.string.manual_coordinates_hint)))
        val latitude = input(body, R.string.latitude, draft.latitude, numeric = true).apply {
            id = R.id.manualLatitudeInput
            imeOptions = EditorInfo.IME_ACTION_NEXT
        }
        val latitudeLayout = body.getChildAt(body.childCount - 1) as TextInputLayout
        latitudeLayout.helperText = string(R.string.latitude_range)
        val longitude = input(body, R.string.longitude, draft.longitude, numeric = true).apply {
            id = R.id.manualLongitudeInput
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val longitudeLayout = body.getChildAt(body.childCount - 1) as TextInputLayout
        longitudeLayout.helperText = string(R.string.longitude_range)
        for (field in listOf(latitude, longitude)) {
            // Accept either decimal separator even when the keyboard uses another locale.
            field.keyListener = DigitsKeyListener.getInstance("0123456789+-.,")
            field.setRawInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)
            field.setSelectAllOnFocus(true)
        }
        fun watch(field: TextInputEditText, layout: TextInputLayout) {
            field.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    layout.error = null
                    onDraftChanged(CoordinateDraft(latitude.text.toString(), longitude.text.toString()))
                }
            })
        }
        watch(latitude, latitudeLayout)
        watch(longitude, longitudeLayout)
        val apply = button(R.string.select_on_map, R.drawable.ic_location).apply {
            id = R.id.applyManualCoordinates
            setOnClickListener {
                val lat = latitude.text.toString()
                val lon = longitude.text.toString()
                val validLatitude = Coordinates.parse(lat, "0") != null
                val validLongitude = Coordinates.parse("0", lon) != null
                latitudeLayout.error = if (validLatitude) null else string(R.string.invalid_latitude)
                longitudeLayout.error = if (validLongitude) null else string(R.string.invalid_longitude)
                val point = Coordinates.parse(lat, lon)
                if (point == null) {
                    (if (!validLatitude) latitude else longitude).requestFocus()
                } else {
                    isEnabled = false
                    onChoose(point) { committed ->
                        isEnabled = true
                        if (committed) dialog.dismiss()
                    }
                }
            }
        }
        body.addView(apply, LinearLayout.LayoutParams(-1, dp(56)))
        body.addView(button(R.string.cancel, textOnly = true).apply {
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(-1, dp(48)))
        longitude.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { apply.performClick(); true } else false
        }
        show(dialog)
    }

    fun search(lookup: PlaceLookup, onChoose: (PlaceResult) -> Unit) {
        val (dialog, body) = sheet(R.string.search_places)
        val field = input(body, R.string.search_hint)
        field.imeOptions = EditorInfo.IME_ACTION_SEARCH
        val search = button(R.string.search_action, R.drawable.ic_search)
        body.addView(search, LinearLayout.LayoutParams(-1, dp(56)))
        val status = label(string(R.string.search_intro))
        body.addView(status)
        val progress = CircularProgressIndicator(activity).apply { isIndeterminate = true; visibility = View.GONE }
        body.addView(progress, LinearLayout.LayoutParams(dp(32), dp(32)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        val list = ListView(activity).apply { dividerHeight = 0; visibility = View.GONE }
        body.addView(list, LinearLayout.LayoutParams(-1, listHeight()))
        val performSearch = {
            val query = field.text.toString().trim()
            if (query.length < 2) field.error = string(R.string.search_hint)
            else {
                (activity.getSystemService(InputMethodManager::class.java)).hideSoftInputFromWindow(field.windowToken, 0)
                progress.visibility = View.VISIBLE; search.isEnabled = false; list.visibility = View.GONE
                status.setText(R.string.searching)
                lookup.search(query) { response ->
                    if (dialog.isShowing) {
                        progress.visibility = View.GONE; search.isEnabled = true
                        response.fold(onSuccess = { places ->
                            status.text = if (places.isEmpty()) string(R.string.search_no_results) + "\n" + string(R.string.search_try_again) else ""
                            list.visibility = if (places.isEmpty()) View.GONE else View.VISIBLE
                            list.adapter = Rows(places.map { it.title to it.address })
                            list.setOnItemClickListener { _, _, position, _ -> onChoose(places[position]); dialog.dismiss() }
                        }, onFailure = { status.text = it.message ?: string(R.string.search_failed) })
                    }
                }
            }
        }
        search.setOnClickListener { performSearch() }
        field.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) { performSearch(); true } else false
        }
        dialog.setOnDismissListener { lookup.cancelSearch(); dialogs.remove(dialog) }
        show(dialog)
    }

    fun library(library: PlaceLibrary, onChoose: (SavedPlace) -> Unit) {
        val (dialog, body) = sheet(R.string.saved_places)
        var history = false
        val tabs = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val favorites = button(R.string.favorites, R.drawable.ic_star, tonal = true)
        val recent = button(R.string.history, R.drawable.ic_history, textOnly = true)
        tabs.addView(favorites, LinearLayout.LayoutParams(0, dp(56), 1f))
        tabs.addView(recent, LinearLayout.LayoutParams(0, dp(56), 1f))
        body.addView(tabs)
        val empty = label("", 16f)
        body.addView(empty)
        val list = ListView(activity).apply { dividerHeight = 0 }
        body.addView(list, LinearLayout.LayoutParams(-1, listHeight()))
        val help = label(string(R.string.manage_place), 12f)
        body.addView(help)
        val clear = button(R.string.clear_history, textOnly = true)
        body.addView(clear)
        var values = emptyList<SavedPlace>()
        fun refresh() {
            values = if (history) library.history() else library.favorites()
            list.adapter = Rows(values.map { it.name to coordinates(it.coordinates) +
                if (history) " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.timestamp)) else "" })
            empty.text = if (history) string(R.string.empty_history) + "\n" + string(R.string.empty_history_hint)
                else string(R.string.empty_favorites) + "\n" + string(R.string.empty_favorites_hint)
            empty.visibility = if (values.isEmpty()) View.VISIBLE else View.GONE
            list.visibility = if (values.isEmpty()) View.GONE else View.VISIBLE
            clear.visibility = if (history && values.isNotEmpty()) View.VISIBLE else View.GONE
            help.visibility = if (history || values.isEmpty()) View.GONE else View.VISIBLE
            val background = color(com.google.android.material.R.attr.colorSecondaryContainer)
            favorites.backgroundTintList = ColorStateList.valueOf(if (history) 0 else background)
            recent.backgroundTintList = ColorStateList.valueOf(if (history) background else 0)
        }
        favorites.setOnClickListener { history = false; refresh() }
        recent.setOnClickListener { history = true; refresh() }
        list.setOnItemClickListener { _, _, position, _ -> onChoose(values[position]); dialog.dismiss() }
        list.setOnItemLongClickListener { _, _, position, _ ->
            if (history) false else {
                val place = values[position]
                show(MaterialAlertDialogBuilder(activity).setTitle(place.name)
                    .setItems(arrayOf(string(R.string.rename), string(R.string.delete))) { _, which ->
                        if (which == 0) nameDialog(place.name, { library.rename(place.id, it) }) { refresh() }
                        else write({ library.deleteFavorite(place.id) }, saved = { refresh() })
                    }.create())
                true
            }
        }
        clear.setOnClickListener { confirm(R.string.clear_history, R.string.clear_history_confirm) { write({ library.clearHistory() }, saved = { refresh() }) } }
        refresh()
        show(dialog)
    }

    fun favorite(library: PlaceLibrary, point: Coordinates, name: String, onChanged: () -> Unit) {
        val existing = library.favoriteAt(point)
        nameDialog(existing?.name ?: name, { library.saveFavorite(it, point) }, onChanged)
    }

    private fun nameDialog(current: String, onSave: (String) -> Unit, saved: () -> Unit) {
        val body = column()
        val edit = input(body, R.string.favorite_name, current)
        val dialog = MaterialAlertDialogBuilder(activity).setTitle(R.string.save_favorite).setView(body)
            .setPositiveButton(R.string.save, null).setNegativeButton(R.string.cancel, null).create()
        show(dialog)
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val name = edit.text.toString().trim()
            if (name.isEmpty()) edit.error = string(R.string.favorite_name)
            else {
                dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).isEnabled = false
                write({ onSave(name) }, { saved(); dialog.dismiss() }, {
                    dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).isEnabled = true
                })
            }
        }
    }

    fun routes(draft: RouteDraft, onEdit: (RouteDraft) -> Unit, onStart: (RouteDraft) -> Unit,
               onImport: (RouteDraft) -> Unit, onExport: (RouteDraft) -> Unit, onClear: () -> Unit, onCalculate: (RouteDraft) -> Unit) {
        val (dialog, body) = sheet(R.string.routes, scroll = true)
        val distance = draft.points.zipWithNext { a, b -> RouteNavigator.distance(a, b) }.sum()
        body.addView(label(if (draft.mode == RouteMode.STREET && !draft.ready) "${draft.editablePoints.size} puntos elegidos · calcula el recorrido"
            else activity.resources.getQuantityString(R.plurals.route_points, draft.points.size, draft.points.size, distance(distance)), 16f, false))
        body.addView(label(if (draft.mode == RouteMode.STREET) "Elige los puntos en el mapa y calcula el recorrido por calles." else string(R.string.route_disclaimer), 13f))
        val mode = Spinner(activity).apply {
            minimumHeight = dp(48)
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, listOf("Ruta directa", "Por calles", "Importada GPX"))
            setSelection(draft.mode.ordinal)
        }
        body.addView(mode)
        val profile = Spinner(activity).apply {
            minimumHeight = dp(48)
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, RouteProfile.entries.map { it.label })
            setSelection(draft.profile.ordinal)
        }
        body.addView(profile)
        if (draft.attribution.isNotEmpty()) body.addView(label(draft.attribution, 12f).apply {
            text = android.text.Html.fromHtml("<a href=\"https://www.geoapify.com/\">Powered by Geoapify</a> · <a href=\"https://www.openstreetmap.org/copyright\">© OpenStreetMap contributors</a>", android.text.Html.FROM_HTML_MODE_LEGACY)
            movementMethod = android.text.method.LinkMovementMethod.getInstance()
        })
        draft.estimatedSeconds?.let { body.addView(label("Tiempo vial estimado: ${(it / 60).toInt()} min. La simulación usa la velocidad indicada abajo.")) }
        val speed = input(body, R.string.route_speed_label, draft.speedKmh.toString(), true)
        val loop = MaterialSwitch(activity).apply { setText(R.string.route_loop_label); isChecked = draft.loop; minHeight = dp(48) }
        body.addView(loop)
        body.addView(label(string(R.string.route_loop_hint), 12f))
        fun current(): RouteDraft? {
            val value = speed.text.toString().trim().replace(',', '.').toDoubleOrNull()
            return if (value == null || !value.isFinite() || value !in 0.5..300.0) {
                speed.error = "0,5–300 km/h"; null
            } else {
                val chosenMode = RouteMode.entries[mode.selectedItemPosition]
                val chosenProfile = RouteProfile.entries[profile.selectedItemPosition]
                val changed = chosenMode != draft.mode || chosenMode == RouteMode.STREET && (chosenProfile != draft.profile || loop.isChecked != draft.loop)
                val base = draft.copy(speedKmh = value, loop = loop.isChecked, mode = chosenMode, profile = chosenProfile)
                if (changed) {
                    if (chosenMode == RouteMode.STREET && draft.editablePoints.size > 20) {
                        Toast.makeText(activity, "Por calles admite hasta 20 puntos. Crea una ruta más corta.", Toast.LENGTH_LONG).show()
                        return null
                    }
                    if (chosenMode == RouteMode.STREET) base.copy(points = emptyList(), waypoints = draft.editablePoints, calculatedLoop = null, estimatedSeconds = null, source = "")
                    else base.copy(points = draft.editablePoints, waypoints = emptyList(), calculatedLoop = null, estimatedSeconds = null, source = if (draft.mode == RouteMode.STREET) "" else draft.source)
                } else base
            }
        }
        body.addView(button(if (draft.points.isEmpty()) R.string.draw_route else R.string.edit_route, R.drawable.ic_route, tonal = true).apply {
            setOnClickListener { current()?.let { onEdit(it); dialog.dismiss() } }
        }, LinearLayout.LayoutParams(-1, dp(56)))
        body.addView(button(R.string.calculate_street_route, R.drawable.ic_route, tonal = true).apply {
            setOnClickListener { current()?.let { route ->
                if (route.mode != RouteMode.STREET) Toast.makeText(activity, "Elige el modo Por calles.", Toast.LENGTH_LONG).show()
                else { onCalculate(route); dialog.dismiss() }
            } }
        }, LinearLayout.LayoutParams(-1, dp(56)))
        body.addView(button(R.string.route_start, R.drawable.ic_play).apply {
            setOnClickListener { current()?.let { route ->
                try { require(route.ready) { "Calcula la ruta por calles o elige al menos dos puntos." }; RouteNavigator(route.points, route.speedKmh, route.loop); onStart(route); dialog.dismiss() }
                catch (error: IllegalArgumentException) { Toast.makeText(activity, error.message, Toast.LENGTH_LONG).show() }
            } }
        }, LinearLayout.LayoutParams(-1, dp(56)))
        val files = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        files.addView(button(R.string.import_gpx, R.drawable.ic_download, textOnly = true).apply {
            setOnClickListener { current()?.let { onImport(it); dialog.dismiss() } }
        }, LinearLayout.LayoutParams(0, dp(56), 1f))
        files.addView(button(R.string.export_gpx, R.drawable.ic_upload, textOnly = true).apply {
            isEnabled = draft.points.size >= 2
            setOnClickListener { current()?.let { onExport(it); dialog.dismiss() } }
        }, LinearLayout.LayoutParams(0, dp(56), 1f))
        body.addView(files)
        body.addView(button(R.string.clear_route, textOnly = true).apply {
            isEnabled = draft.editablePoints.isNotEmpty()
            setOnClickListener { confirm(R.string.clear_route, R.string.clear_route_confirm) { onClear(); dialog.dismiss() } }
        })
        show(dialog)
    }

    fun settings(preferences: AppPreferences, onSave: (SimulationOptions, Boolean, (Boolean) -> Unit) -> Unit, onDeveloper: () -> Unit, onBackup: () -> Unit, privacyOptionsRequired: Boolean, onPrivacyOptions: () -> Unit, onPrivacyPolicy: () -> Unit) {
        val (dialog, body) = sheet(R.string.settings, scroll = true)
        body.addView(button(R.string.developer_settings, R.drawable.ic_settings, tonal = true).apply {
            setOnClickListener { dialog.dismiss(); onDeveloper() }
        }, LinearLayout.LayoutParams(-1, dp(56)))
        body.addView(button(R.string.backup_restore, R.drawable.ic_download, tonal = true).apply {
            setOnClickListener { dialog.dismiss(); onBackup() }
        }, LinearLayout.LayoutParams(-1, dp(56)))
        body.addView(button(R.string.privacy_policy, textOnly = true).apply {
            setOnClickListener { onPrivacyPolicy() }
        })
        if (privacyOptionsRequired) body.addView(button(R.string.privacy_options, textOnly = true).apply {
            setOnClickListener { dialog.dismiss(); onPrivacyOptions() }
        })
        body.addView(label(string(R.string.simulation_options), 18f, false))
        val options = preferences.options()
        val altitude = input(body, R.string.altitude_label, options.altitudeMeters.toString(), true)
        val accuracy = input(body, R.string.accuracy_label, options.accuracyMeters.toString(), true)
        body.addView(label(string(R.string.interval_label)))
        val intervals = listOf(250L, 500L, 1000L, 2000L, 5000L, 10000L)
        val interval = Spinner(activity).apply {
            minimumHeight = dp(48)
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, intervals.map { "$it ms" })
            setSelection(intervals.indexOf(options.intervalMillis).coerceAtLeast(0))
        }
        body.addView(interval)
        body.addView(label(string(R.string.options_hint), 12f))
        val dynamic = MaterialSwitch(activity).apply {
            setText(R.string.dynamic_colors); isChecked = preferences.dynamicColor; minHeight = dp(48)
            isEnabled = Build.VERSION.SDK_INT >= 31
        }
        body.addView(dynamic)
        body.addView(label(string(R.string.dynamic_colors_hint), 12f))
        body.addView(button(R.string.save, R.drawable.ic_check).apply {
            setOnClickListener {
                val result = runCatching {
                    SimulationOptions(altitude.text.toString().replace(',', '.').toDouble(),
                        accuracy.text.toString().replace(',', '.').toFloat(), intervals[interval.selectedItemPosition])
                }
                result.fold(onSuccess = {
                    isEnabled = false
                    onSave(it, dynamic.isChecked) { committed ->
                        isEnabled = true
                        if (committed) dialog.dismiss()
                    }
                },
                    onFailure = { Toast.makeText(activity, R.string.invalid_options, Toast.LENGTH_LONG).show() })
            }
        }, LinearLayout.LayoutParams(-1, dp(56)))
        show(dialog)
    }

    fun mapAppearance(current: String, onChoose: (String) -> Unit) {
        val values = listOf("auto", "light", "dark")
        val labels = arrayOf(string(R.string.map_auto), string(R.string.map_light), string(R.string.map_dark))
        show(MaterialAlertDialogBuilder(activity).setTitle(R.string.map_appearance)
            .setSingleChoiceItems(labels, values.indexOf(current)) { dialog, which -> onChoose(values[which]); dialog.dismiss() }
            .setNegativeButton(R.string.cancel, null).create())
    }

    fun developer(onOpen: () -> Unit) {
        val body = column()
        val steps = label("", 16f, false).apply { minimumHeight = dp(144) }
        body.addView(steps)
        body.addView(label(activity.getString(R.string.developer_guide, string(R.string.app_name))))
        body.addView(label(string(R.string.preparation_mark)))
        val summary = label("", 14f, false)
        body.addView(summary)
        fun refresh() {
            val checks = listOf(
                MockPermissions.isMockApp(activity) to activity.getString(R.string.preparation_mock, string(R.string.app_name)),
                MockPermissions.locationEnabled(activity) to string(R.string.preparation_location),
                MockPermissions.hasLocation(activity) to string(R.string.preparation_permission)
            )
            steps.text = checks.joinToString("\n\n") { (ready, text) -> "${if (ready) "✓ Listo" else "Pendiente"} · $text" }
            summary.setText(if (checks.all { it.first }) R.string.preparation_ready else R.string.preparation_pending)
        }
        refresh()
        val scroll = ScrollView(activity).apply { addView(body) }
        val dialog = MaterialAlertDialogBuilder(activity).setTitle(R.string.preparation_title).setView(scroll)
            .setPositiveButton(R.string.open_settings_action) { _, _ -> onOpen() }
            .setNeutralButton(R.string.check_preparation, null)
            .setNegativeButton(R.string.cancel, null).create()
        show(dialog)
        dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener { refresh() }
    }

    private fun write(action: () -> Unit, saved: () -> Unit, failed: () -> Unit = {}) {
        dataModel.write(activity, action, { saved() }, {
            Toast.makeText(activity, "No se pudieron guardar los cambios: ${it.message}", Toast.LENGTH_LONG).show()
            failed()
        })
    }
    private fun confirm(title: Int, message: Int, confirmed: () -> Unit) {
        show(MaterialAlertDialogBuilder(activity).setTitle(title).setMessage(message)
            .setPositiveButton(R.string.delete) { _, _ -> confirmed() }.setNegativeButton(R.string.cancel, null).create())
    }

    fun closeCoordinates() { coordinateDialog?.dismiss() }
    fun close() { dialogs.toList().forEach { it.dismiss() }; dialogs.clear() }
    fun coordinates(point: Coordinates) = String.format(Locale.US, "%.6f, %.6f", point.latitude, point.longitude)
    private fun distance(meters: Double) = if (meters < 1000) activity.getString(R.string.distance_m, meters.toInt())
        else activity.getString(R.string.distance_km, meters / 1000)
    private fun listHeight() = (activity.resources.displayMetrics.heightPixels * 0.43).toInt().coerceAtMost(dp(400))

    private inner class Rows(private val items: List<Pair<String, String>>) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView as? LinearLayout ?: column(12).apply {
                setPadding(dp(12), dp(10), dp(12), dp(10))
                minimumHeight = dp(72)
                background = AppCompatResources.getDrawable(activity, android.R.drawable.list_selector_background)
                addView(label("", 16f, false).apply { maxLines = 2 })
                addView(label("", 12f).apply { maxLines = 2 })
            }
            (row.getChildAt(0) as TextView).text = items[position].first
            (row.getChildAt(1) as TextView).text = items[position].second
            return row
        }
    }
}
