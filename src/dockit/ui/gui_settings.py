# gui_settings.py - 设置与主界面
"""CustomTkinter：设置、用量、期限（由 Tauri 迁移）"""

import json
import os
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox

import customtkinter as ctk
import yaml
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

logger = None


def _get_logger():
    global logger
    if logger is None:
        import logging
        logger = logging.getLogger(__name__)
    return logger


def load_config(path: Path) -> dict:
    with open(path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f) or {}


def save_config(path: Path, config: dict) -> None:
    with open(path, "w", encoding="utf-8") as f:
        yaml.safe_dump(config, f, allow_unicode=True, default_flow_style=False, sort_keys=False)


def expand_path(p: str) -> str:
    return str(Path(p.replace("$HOME", os.path.expanduser("~"))).expanduser())


def _fetch_usage(api_base: str, token: str) -> dict | None:
    """GET /api/usage"""
    base = api_base.rstrip("/")
    url = f"{base}/api/usage"
    req = Request(url, headers={"Authorization": f"Bearer {token}"})
    try:
        with urlopen(req, timeout=15) as r:
            return json.loads(r.read().decode("utf-8"))
    except (HTTPError, URLError, json.JSONDecodeError):
        return None


def _do_login(parent: ctk.CTk, v_api_base: tk.StringVar, v_token: tk.StringVar, on_success=None) -> None:
    """弹出登录/注册对话框"""
    base = v_api_base.get().strip().rstrip("/")
    if not base:
        messagebox.showwarning("提示", "请先填写 API 地址")
        return

    dlg = ctk.CTkToplevel(parent)
    dlg.title("登录 / 注册")
    dlg.geometry("340x200")
    dlg.transient(parent)
    dlg.grab_set()

    ctk.CTkLabel(dlg, text="邮箱").grid(row=0, column=0, padx=12, pady=8, sticky="w")
    v_email = tk.StringVar()
    ctk.CTkEntry(dlg, textvariable=v_email, width=260).grid(row=0, column=1, padx=12, pady=8)
    ctk.CTkLabel(dlg, text="密码").grid(row=1, column=0, padx=12, pady=8, sticky="w")
    v_pw = tk.StringVar()
    ctk.CTkEntry(dlg, textvariable=v_pw, width=260, show="*").grid(row=1, column=1, padx=12, pady=8)
    v_is_register = tk.BooleanVar(value=False)
    ctk.CTkCheckBox(dlg, text="注册新账号", variable=v_is_register).grid(row=2, column=1, sticky="w", padx=12, pady=4)

    def submit():
        email, pw = v_email.get().strip(), v_pw.get()
        if not email or not pw:
            messagebox.showwarning("提示", "请输入邮箱和密码", parent=dlg)
            return
        path = "/api/auth/register" if v_is_register.get() else "/api/auth/login"
        url = f"{base}{path}"
        body = json.dumps({"email": email, "password": pw}).encode("utf-8")
        req = Request(url, data=body, method="POST", headers={"Content-Type": "application/json"})
        try:
            with urlopen(req, timeout=15) as r:
                data = json.loads(r.read().decode("utf-8"))
            token = data.get("token")
            if token:
                v_token.set(token)
                dlg.destroy()
                messagebox.showinfo("成功", "已自动填入 Token，请点击保存", parent=parent)
                if on_success:
                    on_success()
            else:
                messagebox.showerror("失败", "未返回 token", parent=dlg)
        except HTTPError as e:
            err = e.read().decode("utf-8", errors="replace") if e.fp else str(e)
            messagebox.showerror("请求失败", err[:200], parent=dlg)
        except (URLError, json.JSONDecodeError) as e:
            messagebox.showerror("错误", str(e), parent=dlg)

    ctk.CTkButton(dlg, text="确定", command=submit, width=100).grid(row=3, column=1, pady=16, sticky="w", padx=12)


def _build_settings_tab(tab: ctk.CTkFrame, config_path: Path, config: dict, root: ctk.CTk, refs: dict) -> None:
    """设置 Tab"""
    def pick_dir(var: tk.StringVar, key: str):
        val = var.get().strip() or expand_path(config.get(key, "~"))
        folder = filedialog.askdirectory(initialdir=val, title="选择目录")
        if folder:
            var.set(folder)

    ctk.CTkLabel(tab, text="监听目录").pack(anchor="w", pady=(0, 4))
    f1 = ctk.CTkFrame(tab, fg_color="transparent")
    f1.pack(fill="x", pady=(0, 12))
    v_watch = tk.StringVar(value=expand_path(config.get("watch_dir", "~/Downloads")))
    ctk.CTkEntry(f1, textvariable=v_watch, height=36).pack(side="left", fill="x", expand=True, padx=(0, 8))
    ctk.CTkButton(f1, text="浏览", width=70, command=lambda: pick_dir(v_watch, "watch_dir")).pack(side="right")

    ctk.CTkLabel(tab, text="归档目录").pack(anchor="w", pady=(0, 4))
    f2 = ctk.CTkFrame(tab, fg_color="transparent")
    f2.pack(fill="x", pady=(0, 12))
    v_archive = tk.StringVar(value=expand_path(config.get("archive_dir", "~/Desktop/Dockit归档")))
    ctk.CTkEntry(f2, textvariable=v_archive, height=36).pack(side="left", fill="x", expand=True, padx=(0, 8))
    ctk.CTkButton(f2, text="浏览", width=70, command=lambda: pick_dir(v_archive, "archive_dir")).pack(side="right")

    prefilter = config.get("prefilter") or {}
    v_prefilter = tk.BooleanVar(value=prefilter.get("enabled", True))
    ctk.CTkCheckBox(tab, text="开启预筛（非法律文书跳过 LLM，节省用量）", variable=v_prefilter).pack(anchor="w", pady=(0, 16))

    ctk.CTkLabel(tab, text="API 地址").pack(anchor="w", pady=(0, 4))
    llm = config.get("llm") or {}
    v_api_base = tk.StringVar(value=llm.get("api_base_url") or "http://localhost:8000")
    v_token = tk.StringVar(value=llm.get("api_token") or "")
    refs["api_base"] = v_api_base
    refs["token"] = v_token
    ctk.CTkEntry(tab, textvariable=v_api_base, height=36).pack(fill="x", pady=(0, 12))

    ctk.CTkLabel(tab, text="API Token（登录后自动填入）").pack(anchor="w", pady=(0, 4))
    f5 = ctk.CTkFrame(tab, fg_color="transparent")
    f5.pack(fill="x", pady=(0, 16))
    ctk.CTkEntry(f5, textvariable=v_token, height=36, show="*").pack(side="left", fill="x", expand=True, padx=(0, 8))
    def do_login():
        _do_login(root, v_api_base, v_token, on_success=lambda: getattr(root, "refresh_usage", lambda: None)())
    ctk.CTkButton(f5, text="登录 / 注册", width=100, command=do_login).pack(side="right")

    def on_save():
        try:
            cfg = load_config(config_path)
            cfg["watch_dir"] = v_watch.get().strip()
            cfg["archive_dir"] = v_archive.get().strip()
            if "prefilter" not in cfg:
                cfg["prefilter"] = {}
            cfg["prefilter"]["enabled"] = v_prefilter.get()
            if "llm" not in cfg:
                cfg["llm"] = {}
            cfg["llm"]["api_base_url"] = v_api_base.get().strip()
            cfg["llm"]["api_token"] = v_token.get().strip()
            save_config(config_path, cfg)
            messagebox.showinfo("保存成功", "设置已保存")
            if hasattr(tab.master.master, "refresh_deadlines"):
                tab.master.master.refresh_deadlines()
        except Exception as e:
            _get_logger().exception("保存失败: %s", e)
            messagebox.showerror("保存失败", str(e))

    ctk.CTkButton(tab, text="保存", command=on_save, height=36).pack(anchor="w", pady=(8, 0))


def _build_usage_tab(tab: ctk.CTkFrame, get_api_and_token, refresh_callback) -> None:
    """用量 Tab。get_api_and_token() -> (api_base, token)"""
    frame = ctk.CTkFrame(tab, fg_color="transparent")
    frame.pack(fill="both", expand=True)
    lbl = ctk.CTkLabel(frame, text="加载中…")
    lbl.pack(expand=True)

    def load():
        api_base, token = get_api_and_token()
        if not api_base or not token:
            lbl.configure(text="请先在设置中配置 API 地址并登录")
            return
        data = _fetch_usage(api_base, token)
        if data is None:
            lbl.configure(text="获取用量失败")
            return
        lines = [
            f"套餐: {data.get('tier', '-')}",
            f"本月已用: {data.get('used', 0)} / {data.get('limit', '-')}",
        ]
        if data.get("subscription_ends_at"):
            lines.append(f"订阅到期: {data['subscription_ends_at']}")
        if data.get("subscription_active") is False:
            lines.append("订阅已过期")
        lbl.configure(text="\n".join(lines))

    load()
    if refresh_callback:
        refresh_callback["usage"] = load


def _build_deadlines_tab(tab: ctk.CTkFrame, config_path: Path, refresh_callback) -> None:
    """期限 Tab"""
    from ..db.db import list_deadlines

    scroll = ctk.CTkScrollableFrame(tab, fg_color="transparent")
    scroll.pack(fill="both", expand=True)

    def load():
        for w in scroll.winfo_children():
            w.destroy()
        cfg = load_config(config_path)
        archive_dir = expand_path(cfg.get("archive_dir", "") or "")
        if not archive_dir:
            ctk.CTkLabel(scroll, text="请先在设置中配置归档目录").pack(anchor="w", pady=8)
            return
        rows = list_deadlines(archive_dir)
        if not rows:
            ctk.CTkLabel(scroll, text="暂无期限记录").pack(anchor="w", pady=8)
            return
        ctk.CTkLabel(scroll, text="关键期限", font=ctk.CTkFont(weight="bold")).pack(anchor="w", pady=(0, 8))
        for r in rows:
            text = f"{r['due_date']}  {r['deadline_type']}  {r['case_number']}"
            if r.get("is_completed"):
                text += "  已完成"
            ctk.CTkLabel(scroll, text=text, anchor="w").pack(fill="x", pady=2)

    load()
    if refresh_callback:
        refresh_callback["deadlines"] = load


def run_settings(config_path: Path | None = None) -> None:
    if config_path is None:
        config_path = Path(__file__).resolve().parents[3] / "config.yaml"
    config = load_config(config_path)

    ctk.set_appearance_mode("system")
    ctk.set_default_color_theme("blue")

    root = ctk.CTk()
    root.title("Dockit")
    root.geometry("560x480")
    root.minsize(480, 400)

    # Header: 标题 + 主题切换 + 标签
    header = ctk.CTkFrame(root, fg_color="transparent")
    header.pack(fill="x", padx=20, pady=(16, 8))

    ctk.CTkLabel(header, text="Dockit", font=ctk.CTkFont(size=20, weight="bold")).pack(side="left")
    tabview = ctk.CTkTabview(root, width=500)
    tabview.pack(fill="both", expand=True, padx=20, pady=(0, 16))
    tabview.add("设置")
    tabview.add("用量")
    tabview.add("期限")

    v_theme = tk.StringVar(value=ctk.get_appearance_mode())
    def toggle_theme():
        mode = "dark" if ctk.get_appearance_mode() == "Light" else "light"
        ctk.set_appearance_mode(mode)
    ctk.CTkButton(header, text="🌙" if ctk.get_appearance_mode() == "Light" else "☀️", width=40, command=toggle_theme).pack(side="right", padx=4)

    refresh_handlers = {}
    refs = {}

    _build_settings_tab(tabview.tab("设置"), config_path, config, root, refs)
    def get_api_and_token():
        a, t = refs.get("api_base"), refs.get("token")
        return (a.get() if a else "", t.get() if t else "")
    _build_usage_tab(tabview.tab("用量"), get_api_and_token, refresh_handlers)
    _build_deadlines_tab(tabview.tab("期限"), config_path, refresh_handlers)

    def refresh_usage():
        if "usage" in refresh_handlers:
            refresh_handlers["usage"]()

    def refresh_deadlines():
        if "deadlines" in refresh_handlers:
            refresh_handlers["deadlines"]()

    root.refresh_usage = refresh_usage
    root.refresh_deadlines = refresh_deadlines

    root.mainloop()


if __name__ == "__main__":
    run_settings()
