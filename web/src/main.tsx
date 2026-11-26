import ReactDOM from 'react-dom/client'
import App from './App'
import { registerAppPwa } from './lib/pwa'
import './styles.css'

registerAppPwa()

ReactDOM.createRoot(document.getElementById('root')!).render(<App />)