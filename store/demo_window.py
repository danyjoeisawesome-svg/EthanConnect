"""Full-screen neutral window shown on the PC while taking the Play Store 'mirror' screenshot, so no personal
content appears in the mirrored picture. Close with Esc (or it exits after 90 s)."""
import tkinter as tk

root = tk.Tk()
root.attributes("-fullscreen", True)
root.attributes("-topmost", True)
w, h = root.winfo_screenwidth(), root.winfo_screenheight()
c = tk.Canvas(root, width=w, height=h, highlightthickness=0)
c.pack()
for i in range(h):  # soft blue gradient
    t = i / h
    c.create_line(0, i, w, i, fill="#%02x%02x%02x" % (int(14 + 20 * t), int(24 + 40 * t), int(52 + 80 * t)))
# a window-like card
x0, y0, x1, y1 = w * 0.12, h * 0.14, w * 0.88, h * 0.82
c.create_rectangle(x0, y0, x1, y1, fill="#f8fafc", outline="")
c.create_rectangle(x0, y0, x1, y0 + 46, fill="#e2e8f0", outline="")
for k, col in enumerate(("#f87171", "#fbbf24", "#34d399")):
    c.create_oval(x0 + 18 + k * 26, y0 + 15, x0 + 34 + k * 26, y0 + 31, fill=col, outline="")
c.create_text(w / 2, y0 + 23, text="Project notes", font=("Segoe UI", 14), fill="#334155")
c.create_text(x0 + 60, y0 + 110, anchor="w", text="Weekly plan", font=("Segoe UI", int(h / 22), "bold"), fill="#0f172a")
lines = ["Review the design draft", "Send the budget to the team", "Book the meeting room for Friday",
         "Prepare slides for the demo", "Back up the project files"]
for n, t in enumerate(lines):
    y = y0 + 200 + n * (h / 12)
    c.create_rectangle(x0 + 60, y - 14, x0 + 88, y + 14, outline="#3b82f6", width=3)
    c.create_text(x0 + 110, y, anchor="w", text=t, font=("Segoe UI", int(h / 33)), fill="#1e293b")
c.create_text(w / 2, h * 0.92, text="This is the PC screen, shown on the TV", font=("Segoe UI", int(h / 36)), fill="#cbd5e1")
root.bind("<Escape>", lambda e: root.destroy())
root.after(90000, root.destroy)
root.mainloop()
