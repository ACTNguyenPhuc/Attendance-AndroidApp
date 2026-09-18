import sys, os
if hasattr(sys.stdout,"reconfigure"): sys.stdout.reconfigure(encoding="utf-8",errors="replace")
from dotenv import load_dotenv
load_dotenv(os.path.join(os.path.dirname(__file__),"..",".env"))
from google import genai
from google.genai import types
c=genai.Client(api_key=os.getenv("GOOGLE_API_KEY"))

for m in ["gemini-3.6-flash","gemini-3-flash-preview","gemini-3-pro-preview","gemini-2.5-flash-lite","gemini-2.5-pro"]:
    try:
        r=c.models.generate_content(model=m, contents="Trả lời ngắn: 2+2 bằng mấy?")
        print(f"  ✅ {m:26} -> {r.text.strip()[:40]!r}  ({r.usage_metadata.total_token_count} token)")
    except Exception as e:
        msg=str(e).split("'message':")[-1][:110] if "message" in str(e) else str(e)[:110]
        print(f"  ❌ {m:26} -> {msg}")

print("\n=== THỬ FUNCTION CALLING (thứ chatbot bắt buộc cần) ===")
tool = types.Tool(function_declarations=[types.FunctionDeclaration(
    name="get_attendance",
    description="Lấy số buổi vắng của sinh viên trong một lớp",
    parameters={"type":"OBJECT","properties":{"class_id":{"type":"STRING","description":"Mã lớp"}},"required":["class_id"]},
)])
for m in ["gemini-3.6-flash","gemini-3-flash-preview"]:
    try:
        r=c.models.generate_content(model=m,
            contents="Tôi vắng mấy buổi lớp KTPMN_L03?",
            config=types.GenerateContentConfig(tools=[tool]))
        fc=[p.function_call for p in r.candidates[0].content.parts if p.function_call]
        print(f"  {m:26} -> gọi tool: {[(f.name,dict(f.args)) for f in fc] if fc else 'KHÔNG gọi tool'}")
    except Exception as e:
        print(f"  {m:26} -> LỖI: {str(e)[:90]}")
