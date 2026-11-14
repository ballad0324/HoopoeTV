package com.github.tvbox.osc.mobile.ui.activity;

import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.mobile.ui.MobileDownloadHelper;
import com.github.tvbox.osc.mobile.ui.adapter.MobileDownloadAdapter;

import java.io.File;

/**
 * 下载文件夹：列出已下载（含已转码 mp4）的视频，点击用系统播放器打开，长按删除。
 * 入口在详情页「下载」按钮右侧的文件夹按钮。
 */
public class MobileDownloadsActivity extends BaseActivity {

    private RecyclerView rvFiles;
    private TextView tvEmpty;
    private TextView tvDir;
    private MobileDownloadAdapter adapter;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_downloads_mobile;
    }

    @Override
    protected void init() {
        rvFiles = findViewById(R.id.rvFiles);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvDir = findViewById(R.id.tvDir);

        findViewById(R.id.tvBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.tvRefresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                load();
            }
        });

        adapter = new MobileDownloadAdapter();
        rvFiles.setLayoutManager(new LinearLayoutManager(this));
        rvFiles.setAdapter(adapter);
        adapter.setOnItemAction(new MobileDownloadAdapter.OnItemAction() {
            @Override
            public void onClick(File file) {
                openFile(file);
            }

            @Override
            public void onLongClick(final File file) {
                new AlertDialog.Builder(MobileDownloadsActivity.this)
                        .setTitle(file.getName())
                        .setMessage(R.string.m_delete_file_confirm)
                        .setNegativeButton(R.string.m_cancel, null)
                        .setPositiveButton(R.string.m_delete, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (file.delete()) {
                                    Toast.makeText(MobileDownloadsActivity.this,
                                            R.string.m_deleted, Toast.LENGTH_SHORT).show();
                                    load();
                                }
                            }
                        })
                        .show();
            }
        });

        tvDir.setText(MobileDownloadHelper.downloadDir(this).getAbsolutePath());
        load();
    }

    private void load() {
        java.util.List<File> files = MobileDownloadHelper.listDownloads(this);
        adapter.setData(files);
        if (files.isEmpty()) {
            rvFiles.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            rvFiles.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private void openFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "video/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "没有能打开视频的应用，文件在：\n" + file.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();
        } catch (Throwable th) {
            Toast.makeText(this, "打开失败：" + th.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
