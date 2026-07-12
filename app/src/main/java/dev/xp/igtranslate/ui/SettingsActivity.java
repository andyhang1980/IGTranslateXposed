package dev.xp.igtranslate.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import dev.xp.igtranslate.R;
import dev.xp.igtranslate.translate.Prefs;

/**
 * Settings UI: enable toggle, provider preset (DeepSeek / SiliconFlow / Custom),
 * Base URL / API Key / Model / target language.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String PROVIDER_DEEPSEEK = "deepseek";
    private static final String PROVIDER_SILICONFLOW = "siliconflow";
    private static final String PROVIDER_CUSTOM = "custom";

    private MaterialSwitch enableSwitch;
    private MaterialAutoCompleteTextView providerDropdown;
    private TextInputEditText baseUrlEdit;
    private TextInputEditText apiKeyEdit;
    private TextInputEditText modelEdit;
    private TextInputEditText targetLangEdit;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle(R.string.app_name);

        enableSwitch = findViewById(R.id.switch_enabled);
        providerDropdown = findViewById(R.id.spinner_provider);
        baseUrlEdit = findViewById(R.id.edit_base_url);
        apiKeyEdit = findViewById(R.id.edit_api_key);
        modelEdit = findViewById(R.id.edit_model);
        targetLangEdit = findViewById(R.id.edit_target_lang);

        prefs = getSharedPreferences(Prefs.NAME, Context.MODE_WORLD_READABLE);

        String[] entries = {
                getString(R.string.provider_deepseek),
                getString(R.string.provider_siliconflow),
                getString(R.string.provider_custom)
        };
        providerDropdown.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, entries));
        providerDropdown.setOnItemClickListener((parent, view, position, id) -> applyPreset(position));

        load();
        enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("enabled", isChecked).apply());
        ((MaterialButton) findViewById(R.id.btn_save)).setOnClickListener(v -> save());
    }

    private void applyPreset(int position) {
        if (position == 0) {
            baseUrlEdit.setText("https://api.deepseek.com/v1");
            modelEdit.setText("deepseek-chat");
        } else if (position == 1) {
            baseUrlEdit.setText("https://api.siliconflow.cn/v1");
            modelEdit.setText("Qwen/Qwen2.5-7B-Instruct");
        }
    }

    private void load() {
        enableSwitch.setChecked(prefs.getBoolean("enabled", false));
        baseUrlEdit.setText(prefs.getString("base_url", ""));
        apiKeyEdit.setText(prefs.getString("api_key", ""));
        modelEdit.setText(prefs.getString("model", ""));
        targetLangEdit.setText(prefs.getString("target_lang", "zh-CN"));

        String prov = prefs.getString("provider", PROVIDER_CUSTOM);
        int idx = PROVIDER_DEEPSEEK.equals(prov) ? 0 : PROVIDER_SILICONFLOW.equals(prov) ? 1 : 2;
        String[] entries = {
                getString(R.string.provider_deepseek),
                getString(R.string.provider_siliconflow),
                getString(R.string.provider_custom)
        };
        providerDropdown.setText(entries[idx], false);
    }

    private void save() {
        String base = baseUrlEdit.getText() == null ? "" : baseUrlEdit.getText().toString().trim();
        String key = apiKeyEdit.getText() == null ? "" : apiKeyEdit.getText().toString().trim();
        String model = modelEdit.getText() == null ? "" : modelEdit.getText().toString().trim();
        String lang = targetLangEdit.getText() == null ? "zh-CN" : targetLangEdit.getText().toString().trim();
        String provLabel = providerDropdown.getText() == null ? "" : providerDropdown.getText().toString();
        String prov = provLabel.equals(getString(R.string.provider_deepseek)) ? PROVIDER_DEEPSEEK
                : provLabel.equals(getString(R.string.provider_siliconflow)) ? PROVIDER_SILICONFLOW : PROVIDER_CUSTOM;

        prefs.edit()
                .putBoolean("enabled", enableSwitch.isChecked())
                .putString("provider", prov)
                .putString("base_url", base)
                .putString("api_key", key)
                .putString("model", model)
                .putString("target_lang", lang)
                .apply();

        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        finish();
    }
}
