import {NavLink, Outlet} from 'react-router';
import reactLogo from './assets/react.svg'
import orbitalLogo from '/logo.png'
import classes from './App.module.css'
import {examples} from './main.tsx';

function App() {
  return (
    <>
      <div className={classes.logoContainer}>
        <a href="https://orbitalhq.com" target="_blank">
          <img src={orbitalLogo} className={classes.logo} alt="Vite logo"/>
        </a>
        <span className={classes.plus}>+</span>
        <a href="https://react.dev" target="_blank">
          <img src={reactLogo} className={`${classes.logo} ${classes.react}`} alt="React logo"/>
        </a>
      </div>

      <div className={classes.navLinks}>
        {examples.map(({to, label}, index) => (
          <NavLink key={index} to={to} className={({ isActive }) => isActive ? classes.active : "" }>
            {label}
          </NavLink>
        ))}

      </div>
      <div className={classes.mainContainer}>
        {/*<Queries/>*/}
        <Outlet/>
      </div>
    </>
  )
}

export default App
