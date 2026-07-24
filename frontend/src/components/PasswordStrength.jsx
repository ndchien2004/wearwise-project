/**
 * Thanh đo độ mạnh mật khẩu. Ba tiêu chí đầu khớp đúng ràng buộc @StrongPassword
 * phía backend, tiêu chí thứ tư chỉ là gợi ý để mật khẩu chắc hơn.
 */

const LEVELS = [
  { label: 'Rất yếu', color: 'var(--red)' },
  { label: 'Yếu', color: 'var(--red)' },
  { label: 'Tạm được', color: 'var(--orange)' },
  { label: 'Khá', color: 'var(--yellow)' },
  { label: 'Mạnh', color: 'var(--green)' },
];

export function checkPassword(password = '') {
  const rules = [
    { label: 'Từ 8 ký tự trở lên', ok: password.length >= 8 },
    { label: 'Có ít nhất 1 chữ cái', ok: /\p{L}/u.test(password) },
    { label: 'Có ít nhất 1 chữ số', ok: /\d/.test(password) },
    { label: 'Có ký tự đặc biệt hoặc dài từ 12 ký tự', ok: /[^\p{L}\d]/u.test(password) || password.length >= 12 },
  ];

  const hasWhitespace = /\s/.test(password);
  const score = hasWhitespace ? 0 : rules.filter((rule) => rule.ok).length;

  // Ba tiêu chí đầu là bắt buộc — backend sẽ từ chối nếu thiếu bất kỳ tiêu chí nào.
  const meetsPolicy = !hasWhitespace && rules.slice(0, 3).every((rule) => rule.ok);

  return { score, meetsPolicy, hasWhitespace, rules, ...LEVELS[score] };
}

export default function PasswordStrength({ password }) {
  if (!password) return null;

  const { score, label, color, hasWhitespace, rules } = checkPassword(password);

  return (
    <div className="pw-strength">
      <div className="pw-strength-bar" role="img" aria-label={`Độ mạnh mật khẩu: ${label}`}>
        {[0, 1, 2, 3].map((index) => (
          <span
            key={index}
            className="pw-strength-seg"
            style={{ background: index < score ? color : 'transparent' }}
          />
        ))}
      </div>
      <div className="pw-strength-label" style={{ color: score >= 3 ? 'var(--ink)' : color }}>
        {label}
      </div>
      <ul className="pw-strength-rules">
        {hasWhitespace && <li className="is-missing">✕ Không được chứa khoảng trắng</li>}
        {rules.map((rule) => (
          <li key={rule.label} className={rule.ok ? 'is-ok' : 'is-missing'}>
            {rule.ok ? '✓' : '✕'} {rule.label}
          </li>
        ))}
      </ul>
    </div>
  );
}
